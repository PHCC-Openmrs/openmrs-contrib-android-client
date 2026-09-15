package org.openmrs.mobile.services;

import static com.openmrs.android_sdk.utilities.ApplicationConstants.ConceptDownloadService.CHANNEL_DESC;
import static com.openmrs.android_sdk.utilities.ApplicationConstants.ConceptDownloadService.CHANNEL_ID;
import static com.openmrs.android_sdk.utilities.ApplicationConstants.ConceptDownloadService.CHANNEL_NAME;

import javax.inject.Inject;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.openmrs.android_sdk.library.api.RestApi;
import com.openmrs.android_sdk.library.api.repository.FormRepository;
import com.openmrs.android_sdk.library.dao.ConceptRoomDAO;
import com.openmrs.android_sdk.library.databases.AppDatabase;
import com.openmrs.android_sdk.library.databases.entities.ConceptEntity;
import com.openmrs.android_sdk.library.models.Link;
import com.openmrs.android_sdk.library.models.Results;
import com.openmrs.android_sdk.library.models.SystemSetting;
import com.openmrs.android_sdk.utilities.ApplicationConstants;

import org.openmrs.mobile.R;
import org.openmrs.mobile.activities.settings.SettingsActivity;
import org.openmrs.mobile.application.OpenMRS;
import org.openmrs.mobile.utilities.PrivilegeUtils;

import rx.Observable;
import rx.schedulers.Schedulers;

@AndroidEntryPoint
public class ConceptDownloadService extends Service {
    private int downloadedConcepts;
    private int maxConceptsInOneQuery = 100;
    // Concept downloading and form schema resolution run concurrently but independently (they
    // touch unrelated data, and one failing shouldn't affect the other) - these track whether
    // each has finished, so the service stays alive (and its foreground notification visible)
    // until both are done, rather than stopping - and losing its claim on the process - the
    // moment whichever one happens to finish first does.
    private volatile boolean conceptsDownloadFinished = false;
    private volatile boolean formSchemasResolved = false;
    @Inject
    RestApi service;
    @Inject
    FormRepository formRepository;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(ApplicationConstants.ServiceActions.START_CONCEPT_DOWNLOAD_ACTION)) {
            conceptsDownloadFinished = false;
            formSchemasResolved = false;
            showNotification(downloadedConcepts);
            startDownload();
            downloadConcepts(downloadedConcepts);
            resolveFormSchemasForOfflineUse();
        } else if (intent.getAction().equals(
                ApplicationConstants.ServiceActions.STOP_CONCEPT_DOWNLOAD_ACTION)) {
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    private void startDownload() {
        Call<Results<SystemSetting>> call = service.getSystemSettingsByQuery(
                ApplicationConstants.SystemSettingKeys.WS_REST_MAX_RESULTS_ABSOLUTE,
                ApplicationConstants.API.FULL);
        call.enqueue(new Callback<Results<SystemSetting>>() {
            @Override
            public void onResponse(@NonNull Call<Results<SystemSetting>> call, @NonNull Response<Results<SystemSetting>> response) {
                if (response.isSuccessful()) {
                    List<SystemSetting> results = null;
                    if (response.body() != null) {
                        results = response.body().getResults();
                        if (results.size() >= 1) {
                            String value = results.get(0).getValue();
                            if (value != null) {
                                maxConceptsInOneQuery = Integer.parseInt(value);
                            }
                        }
                    }
                }
                downloadConcepts(0);
            }

            @Override
            public void onFailure(@NonNull Call<Results<SystemSetting>> call, @NonNull Throwable t) {
                downloadConcepts(0);
            }
        });
    }

    private void showNotification(int downloadedConcepts) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channelPayment = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            channelPayment.setDescription(CHANNEL_DESC);
            notificationManager.createNotificationChannel(channelPayment);
        }

        Intent notificationIntent = new Intent(this, SettingsActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notificationIntent.putExtra(ApplicationConstants.BroadcastActions.CONCEPT_DOWNLOAD_BROADCAST_INTENT_KEY_COUNT, downloadedConcepts);
        int pendingIntentFlags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags);

        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_openmrs);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.downloading_concepts_notification_message))
                .setTicker(getString(R.string.app_name))
                .setContentText(String.valueOf(downloadedConcepts))
                .setSmallIcon(R.drawable.ic_stat_notify_download)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        startForeground(ApplicationConstants.ServiceNotificationId.CONCEPT_DOWNLOADFOREGROUND_SERVICE,
                notification);
    }

    private void downloadConcepts(int startIndex) {
        Call<Results<ConceptEntity>> call = service.getConcepts(maxConceptsInOneQuery, startIndex);
        call.enqueue(new Callback<Results<ConceptEntity>>() {
            @Override
            public void onResponse(@NonNull Call<Results<ConceptEntity>> call, @NonNull Response<Results<ConceptEntity>> response) {
                if (response.isSuccessful()) {
                    ConceptRoomDAO conceptDAO = AppDatabase.getDatabase(OpenMRS.getInstance().getApplicationContext()).conceptRoomDAO();
                    if (response.body() != null) {
                        for (ConceptEntity concept : response.body().getResults()) {
                            conceptDAO.addConcept(concept);
                            downloadedConcepts++;
                        }
                    }

                    showNotification(downloadedConcepts);
                    sendProgressBroadcast();

                    boolean isNextPage = false;
                    if (response.body() != null) {
                        for (Link link : response.body().getLinks()) {
                            if ("next".equals(link.getRel())) {
                                isNextPage = true;
                                downloadConcepts(startIndex + maxConceptsInOneQuery);
                                break;
                            }
                        }
                    }
                    if (!isNextPage) {
                        finishConceptsDownload();
                    }
                } else {
                    finishConceptsDownload();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Results<ConceptEntity>> call, @NonNull Throwable t) {
                finishConceptsDownload();
            }
        });
    }

    private void finishConceptsDownload() {
        conceptsDownloadFinished = true;
        stopIfBothFinished();
    }

    private void finishFormSchemaResolution() {
        formSchemasResolved = true;
        stopIfBothFinished();
    }

    private void stopIfBothFinished() {
        if (conceptsDownloadFinished && formSchemasResolved) {
            stopSelf();
        }
    }

    /**
     * Runs alongside the concept download so that tapping "Download Concepts" while online also
     * primes every form for offline use - some servers store a form's schema out-of-line as clob
     * data, which otherwise only gets fetched (and cached) the first time a user happens to open
     * that specific form while online, silently leaving it unusable offline until then. Targets a
     * different set of data than the concept download, so a failure here doesn't affect it (or
     * vice versa) - but the service must not stop (see [stopIfBothFinished]) until this finishes
     * too, or the process could lose its foreground-service standing (and get killed) while these
     * per-form network calls are still in flight.
     *
     * Explicitly (re-)syncs the form list itself, synchronously, before resolving any schemas -
     * schema resolution only works on forms that already exist as local rows, and relying on
     * FormListService (started independently, e.g. at login) to have already populated them was a
     * real, unsynchronized race: FormListService needs a full network round trip while this
     * service's schema resolution starts almost immediately, so it could - and did - run against
     * an empty or stale form list and silently cache nothing, even though this whole operation
     * still reported success. Doing our own sync here first removes the dependency on that other
     * service's timing entirely.
     */
    private void resolveFormSchemasForOfflineUse() {
        Observable.fromCallable(() -> {
                    if (PrivilegeUtils.hasAnyPrivilege(ApplicationConstants.Privileges.ADD_ENCOUNTERS, ApplicationConstants.Privileges.FORM_ENTRY)) {
                        formRepository.syncFormList();
                    }
                    return true;
                })
                .subscribeOn(Schedulers.io())
                .flatMap(ignored -> formRepository.resolveAllFormSchemas())
                .subscribe(result -> finishFormSchemaResolution(), throwable -> finishFormSchemaResolution());
    }

    private void sendProgressBroadcast() {
        Intent intent = new Intent(ApplicationConstants.BroadcastActions.CONCEPT_DOWNLOAD_BROADCAST_INTENT_ID);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

# Consumer R8 rules shipped with the Aura Logger SDK.
# Room, WorkManager and DataStore ship their own consumer rules.

# WorkManager stores the worker class name of scheduled work and instantiates it by reflection
# with the (Context, WorkerParameters) constructor; keep the name stable across releases.
-keep class com.usatov.aura.logger.internal.cleanup.CleanupWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

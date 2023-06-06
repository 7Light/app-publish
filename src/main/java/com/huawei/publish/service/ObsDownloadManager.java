package com.huawei.publish.service;


import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.DownloadFileRequest;
import com.obs.services.model.MonitorableProgressListener;

import java.util.Date;

/**
 * @author xiongfengbo
 */
public class ObsDownloadManager {
    private DownloadFileRequest request;
    private ObsClient obsClient;
    private MonitorableProgressListener progressListener;
    private Thread currentThread;

    public ObsDownloadManager(ObsClient obsClient, DownloadFileRequest request, MonitorableProgressListener progressListener) {
        this.obsClient = obsClient;
        this.request = request;
        this.progressListener = progressListener;
        request.setProgressListener(progressListener);
    }

    /**
     * Start a download task.
     */
    public void download() {
        this.progressListener.reset();
        this.currentThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Use resumable download.
                    obsClient.downloadFile(request);
                } catch (ObsException e) {
                    // When an exception occurs, you can call the interface for resumable download to download the file again.
                    if (null != e.getCause()
                            && e.getCause() instanceof InterruptedException) {
                        System.out.println(new Date() + "  current thread is interrupted. \n");
                    } else {
                        e.printStackTrace();
                    }
                }
            }
        });

        this.currentThread.start();
    }

    /**
     * Pause a task.
     *
     * @throws InterruptedException 异常
     */
    public void pause() throws InterruptedException {
        // Interrupt the thread to simulate the suspension of the download task.
        // Note: After this method is called, the download task does not stop immediately. You are advised to call the progressListener.waitingFinish method to wait until the task is complete.
        // In addition, after this method is called, "java.lang.RuntimeException: Abort io due to thread interrupted” is displayed in the log. This is a normal phenomenon after the thread is interrupted. You can ignore this exception.
        this.currentThread.interrupt();

        // Wait until the download task is complete.
        this.progressListener.waitingFinish();
    }

    /**
     * Wait until the download task is complete.
     *
     * @throws InterruptedException 异常
     */
    public void waitingFinish() throws InterruptedException {
        this.currentThread.join();
    }
}
package com.manuelmaly.hn.task;

import java.util.HashMap;

import android.app.Activity;
import android.util.Log;

import com.manuelmaly.hn.App;
import com.manuelmaly.hn.model.HNPostComments;
import com.manuelmaly.hn.parser.HNCommentsParser;
import com.manuelmaly.hn.reuse.CancelableRunnable;
import com.manuelmaly.hn.server.HTMLDownloadCommand;
import com.manuelmaly.hn.server.IAPICommand;
import com.manuelmaly.hn.server.IAPICommand.RequestType;
import com.manuelmaly.hn.util.Const;
import com.manuelmaly.hn.util.ExceptionUtil;
import com.manuelmaly.hn.util.FileUtil;
import com.manuelmaly.hn.util.Run;

public class HNPostCommentsTask extends BaseTask<HNPostComments> {

    public static final String BROADCAST_INTENT_ID = "HNPostComments";
    private static HashMap<String, HNPostCommentsTask> runningInstances = new HashMap<String, HNPostCommentsTask>();

    private String mPostID; // for which post shall comments be loaded?

    private HNPostCommentsTask(String postID) {
        super(BROADCAST_INTENT_ID);
        mPostID = postID;
    }

    /**
     * I know, Singleton is generally a no-no, but the only other option would
     * be to store the currently running HNPostCommentsTasks in the App object,
     * which I consider far worse. If you find a better solution, please tweet
     * me at @manuelmaly
     * 
     * @return
     */
    private static HNPostCommentsTask getInstance(String postID) {
        synchronized (HNPostCommentsTask.class) {
            if (!runningInstances.containsKey(postID))
                runningInstances.put(postID, new HNPostCommentsTask(postID));
        }
        return runningInstances.get(postID);
    }

    public static void startOrReattach(Activity activity, ITaskFinishedHandler<HNPostComments> finishedHandler,
        String postID) {
        HNPostCommentsTask task = getInstance(postID);
        task.setOnFinishedHandler(activity, finishedHandler, HNPostComments.class);
        if (!task.isRunning())
            task.startInBackground();
    }

    public static void stopCurrent(String postID) {
        getInstance(postID).cancel();
    }

    public static boolean isRunning(String postID) {
        return getInstance(postID).isRunning();
    }

    @Override
    public CancelableRunnable getTask() {
        return new HNPostCommentsTaskRunnable();
    }

    class HNPostCommentsTaskRunnable extends CancelableRunnable {

        HTMLDownloadCommand mFeedDownload;

        @Override
        public void run() {
            mFeedDownload = new HTMLDownloadCommand("http://news.ycombinator.com/item", "id=" + mPostID,
                RequestType.GET, false, null, App.getInstance());
            mFeedDownload.run();

            if (mCancelled)
                mErrorCode = IAPICommand.ERROR_CANCELLED_BY_USER;
            else
                mErrorCode = mFeedDownload.getErrorCode();

            if (!mCancelled && mErrorCode == IAPICommand.ERROR_NONE) {
                HNCommentsParser commentsParser = new HNCommentsParser();
                try {
                    mResult = commentsParser.parse(mFeedDownload.getResponseContent());
                    Run.inBackground(new Runnable() {
                        public void run() {
                            FileUtil.setLastHNPostComments(mResult, mPostID);
                        }
                    });
                } catch (Exception e) {
                    ExceptionUtil.sendToGoogleAnalytics(e, Const.GAN_ACTION_PARSING);
                    Log.e("HNFeedTask", "Parse error!", e);
                }
            }

            if (mResult == null)
                mResult = new HNPostComments();
        }

        @Override
        public void onCancelled() {
            mFeedDownload.cancel();
        }

    }

}

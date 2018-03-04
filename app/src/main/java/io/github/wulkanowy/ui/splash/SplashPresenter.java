package io.github.wulkanowy.ui.splash;

import android.support.annotation.NonNull;

import javax.inject.Inject;

import io.github.wulkanowy.data.RepositoryContract;
import io.github.wulkanowy.ui.base.BasePresenter;

public class SplashPresenter extends BasePresenter<SplashContract.View>
        implements SplashContract.Presenter {

    @Inject
    SplashPresenter(RepositoryContract repository) {
        super(repository);
    }

    @Override
    public void onStart(@NonNull SplashContract.View activity) {
        super.onStart(activity);
        getView().startSyncService();

        if (getRepository().getCurrentUserId() == 0) {
            getView().openLoginActivity();
        } else {
            getView().openMainActivity();
        }
    }
}
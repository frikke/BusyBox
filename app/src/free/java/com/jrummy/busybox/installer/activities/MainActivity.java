/*
 * Copyright (C) 2016 Jared Rummler <jared.rummler@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jrummy.busybox.installer.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.TransactionDetails;
import com.crashlytics.android.Crashlytics;
import com.google.ads.consent.ConsentInfoUpdateListener;
import com.google.ads.consent.ConsentInformation;
import com.google.ads.consent.ConsentStatus;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.jrummy.busybox.installer.dialogs.RootCheckDialog;
import com.jrummy.busybox.installer.utils.DeviceNameHelper;
import com.jrummy.busybox.installer.utils.RootChecker;
import com.jrummyapps.android.analytics.Analytics;
import com.jrummyapps.android.animations.Technique;
import com.jrummyapps.android.app.App;
import com.jrummyapps.android.prefs.Prefs;
import com.jrummyapps.android.roottools.checks.RootCheck;
import com.jrummyapps.android.util.DeviceUtils;
import com.jrummyapps.android.util.Jot;
import com.jrummyapps.busybox.R;
import com.jrummyapps.busybox.activities.SettingsActivity;
import com.jrummyapps.busybox.utils.Monetize;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class MainActivity extends com.jrummyapps.busybox.activities.MainActivity
    implements BillingProcessor.IBillingHandler {

    private static final String EXTRA_ROOT_DIALOG_SHOWN = "extraRootDialogShown";

    public static Intent linkIntent(Context context, String link) {
        return new Intent(context, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_URI_KEY, link);
    }

    BillingProcessor bp;

    private AdView[] adViewTiers;

    private int currentAdViewIndex;

    private boolean rootDialogShown;

    private RelativeLayout adContainer;

    private InterstitialAd[] interstitialsTabAd;
    private InterstitialAd[] interstitialsSettingsAd;
    private InterstitialAd[] interstitialsInstallAd;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            rootDialogShown = savedInstanceState.getBoolean(EXTRA_ROOT_DIALOG_SHOWN);
        }

        if (!rootDialogShown) {
            RootChecker.execute();
        }

        EventBus.getDefault().register(this);
        checkGdprConsent();

        adContainer = (RelativeLayout) findViewById(R.id.ad_view);
        bp = new BillingProcessor(this, Monetize.decrypt(Monetize.ENCRYPTED_LICENSE_KEY), this);

        if (Prefs.getInstance().get("loaded_purchases_from_google", true)) {
            Prefs.getInstance().save("loaded_purchases_from_google", false);
            bp.loadOwnedPurchasesFromGoogle();
        }

        if (!Monetize.isAdsRemoved()) {
            currentAdViewIndex = 0;
            adViewTiers = new AdView[getResources().getStringArray(R.array.banners_id).length];
            setupBanners();

            viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                }

                @Override public void onPageSelected(int position) {
                    showTabInterstitials();
                }

                @Override public void onPageScrollStateChanged(int state) {
                }
            });

            interstitialsTabAd = new InterstitialAd[getResources()
                .getStringArray(R.array.tabs_interstitials_id).length];
            interstitialsSettingsAd = new InterstitialAd[getResources()
                .getStringArray(R.array.settings_interstitials_id).length];
            interstitialsInstallAd = new InterstitialAd[getResources()
                .getStringArray(R.array.install_interstitials_id).length];

            setupTabInterstitialsAd();
            setupSettingsInterstitialsAd();
            setupInstallInterstitialsAd();
        } else {
            adContainer.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        if (!Monetize.isAdsRemoved()) {
            for (AdView adView : adViewTiers) {
                if (adView != null) {
                    adView.pause();
                }
            }
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Monetize.isAdsRemoved()) {
            for (AdView adView : adViewTiers) {
                if (adView != null) {
                    adView.resume();
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        if (bp != null) {
            bp.release();
        }
        if (!Monetize.isAdsRemoved()) {
            for (AdView adView : adViewTiers) {
                if (adView != null) {
                    adView.destroy();
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_ROOT_DIALOG_SHOWN, rootDialogShown);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (bp.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_remove_ads).setVisible(!Monetize.isAdsRemoved());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_settings) {
            showSettingsInterstitials();
            return true;
        } else if (itemId == R.id.action_remove_ads) {
            Analytics.newEvent("remove ads menu item").log();
            onEventMainThread(new Monetize.Event.RequestRemoveAds());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onProductPurchased(String productId, TransactionDetails details) {
        // Called when requested PRODUCT ID was successfully purchased
        Analytics.newEvent("in-app purchase").put("product_id", productId).log();
        if (productId.equals(Monetize.decrypt(Monetize.ENCRYPTED_REMOVE_ADS_PRODUCT_ID))) {
            Monetize.removeAds();
            EventBus.getDefault().post(new Monetize.Event.OnAdsRemovedEvent());
        }
    }

    @Override
    public void onPurchaseHistoryRestored() {
        // Called when requested PRODUCT ID was successfully purchased
        Jot.d("Restored purchases");
    }

    @Override
    public void onBillingError(int errorCode, Throwable error) {
        // Called when some error occurred. See Constants class for more details
        Analytics.newEvent("billing error").put("error_code", errorCode).log();
        Crashlytics.logException(error);
    }

    @Override
    public void onBillingInitialized() {
        // Called when BillingProcessor was initialized and it's ready to purchase
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(Monetize.Event.RequestInterstitialAd event) {
        showInstallInterstitials();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(Monetize.Event.OnAdsRemovedEvent event) {
        Technique.SLIDE_OUT_DOWN.getComposer().hideOnFinished().playOn(findViewById(R.id.ad_view));

        interstitialsTabAd = null;
        interstitialsSettingsAd = null;
        interstitialsInstallAd = null;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(Monetize.Event.RequestRemoveAds event) {
        bp.purchase(this, Monetize.decrypt(Monetize.ENCRYPTED_REMOVE_ADS_PRODUCT_ID));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventRootCheck(RootCheck rootCheck) {
        if (!rootCheck.accessGranted) {
            RootCheckDialog.show(this, DeviceNameHelper.getSingleton().getName());
            rootDialogShown = true;
        }
    }

    private void checkGdprConsent() {
        ConsentInformation consentInformation = ConsentInformation.getInstance(getApplicationContext());
        String[] publisherIds = {"pub-4229758926684576"};
        consentInformation.requestConsentInfoUpdate(publisherIds, new ConsentInfoUpdateListener() {
            @Override
            public void onConsentInfoUpdated(ConsentStatus consentStatus) {
                boolean isEea = ConsentInformation.getInstance(getApplicationContext()).
                        isRequestLocationInEeaOrUnknown();
                if (isEea) {
                    if (consentStatus == ConsentStatus.UNKNOWN) {
                        //displayConsentForm();
                        displayCustomConsetForm();
                    }
                }else{
                    Log.i("Ads","User is not in EU");
                }
            }

            @Override
            public void onFailedToUpdateConsentInfo(String errorDescription) {
                // User's consent status failed to update.
                Toast.makeText(getApplicationContext(), errorDescription, Toast.LENGTH_SHORT)
                        .show();

            }
        });
    }


    private void displayCustomConsetForm() {

        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(getString(R.string.gdpr_consent_dialog_title));
        alertDialog.setMessage(Html.fromHtml("To keep using Busy Box, confirm that you are agree to our <a href=\"http://maplemedia.io/privacy\">Terms of Service</a> and <a href=\"http://maplemedia.io/privacy\">Privacy Policy."));
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE,getString(R.string.gdpr_consent_dialog_positive_btn), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // We update the user consent status for PERSONALIZED ads.
                ConsentInformation.getInstance(getBaseContext()).setConsentStatus(ConsentStatus.PERSONALIZED);
            }
        });
        alertDialog.setCancelable(false);
        alertDialog.show();
        ((TextView)alertDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());

    }

    private void showTabInterstitials() {
        if (interstitialsTabAd != null) {
            for (InterstitialAd interstitialAd : interstitialsTabAd) {
                if (interstitialIsReady(interstitialAd)) {
                    interstitialAd.show();
                    return;
                }
            }
        }
    }

    private void showSettingsInterstitials() {
        if (interstitialsSettingsAd != null) {
            for (InterstitialAd interstitialAd : interstitialsSettingsAd) {
                if (interstitialIsReady(interstitialAd)) {
                    interstitialAd.show();
                    return;
                }
            }
            startActivity(new Intent(this, SettingsActivity.class));
        }
    }

    private void showInstallInterstitials() {
        if (interstitialsInstallAd != null) {
            for (InterstitialAd interstitialAd : interstitialsInstallAd) {
                if (interstitialIsReady(interstitialAd)) {
                    interstitialAd.show();
                    Analytics.newEvent("interstitial_ad").put("id", interstitialAd.getAdUnitId()).log();
                    return;
                }
            }
        }
    }

    private void setupBanners() {
        AdRequest.Builder builder = new AdRequest.Builder();
        if (App.isDebuggable()) {
            builder.addTestDevice(DeviceUtils.getDeviceId());
        }

        adViewTiers[currentAdViewIndex] = new AdView(this);
        adViewTiers[currentAdViewIndex].setAdSize(AdSize.SMART_BANNER);
        adViewTiers[currentAdViewIndex]
            .setAdUnitId(getResources().getStringArray(R.array.banners_id)[currentAdViewIndex]);
        adViewTiers[currentAdViewIndex].setAdListener(new AdListener() {
            @Override public void onAdFailedToLoad(int errorCode) {
                if (currentAdViewIndex != (adViewTiers.length - 1)) {
                    currentAdViewIndex++;
                    setupBanners();
                } else if (adContainer.getVisibility() == View.VISIBLE) {
                    Technique.SLIDE_OUT_DOWN.getComposer().hideOnFinished().playOn(adContainer);
                }
            }

            @Override public void onAdLoaded() {
                adContainer.setVisibility(View.VISIBLE);
                if (adContainer.getChildCount() != 0) {
                    adContainer.removeAllViews();
                }
                adContainer.addView(adViewTiers[currentAdViewIndex]);
                Analytics.newEvent("on_ad_loaded")
                    .put("id", adViewTiers[currentAdViewIndex].getAdUnitId()).log();
            }
        });

        adViewTiers[currentAdViewIndex].loadAd(builder.build());
    }

    private void setupTabInterstitialsAd() {
        String[] ids = getResources().getStringArray(R.array.tabs_interstitials_id);

        for (int i = 0; i < interstitialsTabAd.length; i++) {
            if (!interstitialIsReady(interstitialsTabAd[i])) {
                final int finalI = i;

                AdListener adListener = new AdListener() {
                    @Override public void onAdClosed() {
                        super.onAdClosed();
                        interstitialsTabAd[finalI] = null;
                        setupTabInterstitialsAd();
                    }
                };

                interstitialsTabAd[i] = newInterstitialAd(ids[i], adListener);
            }
        }
    }

    private void setupSettingsInterstitialsAd() {
        String[] ids = getResources().getStringArray(R.array.settings_interstitials_id);

        for (int i = 0; i < interstitialsSettingsAd.length; i++) {
            if (!interstitialIsReady(interstitialsSettingsAd[i])) {
                final int finalI = i;

                AdListener adListener = new AdListener() {
                    @Override public void onAdClosed() {
                        super.onAdClosed();
                        interstitialsSettingsAd[finalI] = null;
                        setupSettingsInterstitialsAd();
                        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    }
                };

                interstitialsSettingsAd[i] = newInterstitialAd(ids[i], adListener);
            }
        }
    }

    private void setupInstallInterstitialsAd() {
        String[] ids = getResources().getStringArray(R.array.install_interstitials_id);

        for (int i = 0; i < interstitialsInstallAd.length; i++) {
            if (!interstitialIsReady(interstitialsInstallAd[i])) {
                final int finalI = i;

                AdListener adListener = new AdListener() {
                    @Override public void onAdClosed() {
                        super.onAdClosed();
                        interstitialsInstallAd[finalI] = null;
                        setupInstallInterstitialsAd();
                    }
                };

                interstitialsInstallAd[i] = newInterstitialAd(ids[i], adListener);
            }
        }
    }

    private InterstitialAd newInterstitialAd(String placementId, AdListener listener) {
        InterstitialAd interstitialAd = new InterstitialAd(this);
        interstitialAd.setAdListener(listener);
        interstitialAd.setAdUnitId(placementId);
        interstitialAd.loadAd(getAdRequest());
        return interstitialAd;
    }

    private boolean interstitialIsReady(InterstitialAd interstitialAd) {
        return interstitialAd != null && interstitialAd.isLoaded();
    }

    private AdRequest getAdRequest() {
        AdRequest adRequest;
        if (App.isDebuggable()) {
            adRequest = new AdRequest.Builder().addTestDevice(DeviceUtils.getDeviceId()).build();
        } else {
            adRequest = new AdRequest.Builder().build();
        }
        return adRequest;
    }

}

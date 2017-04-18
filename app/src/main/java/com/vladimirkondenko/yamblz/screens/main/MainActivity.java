package com.vladimirkondenko.yamblz.screens.main;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import com.jakewharton.rxbinding2.view.RxView;
import com.jakewharton.rxbinding2.widget.RxAdapterView;
import com.vladimirkondenko.yamblz.App;
import com.vladimirkondenko.yamblz.Const;
import com.vladimirkondenko.yamblz.R;
import com.vladimirkondenko.yamblz.dagger.modules.MainPresenterModule;
import com.vladimirkondenko.yamblz.databinding.ActivityMainBinding;
import com.vladimirkondenko.yamblz.databinding.LayoutTranslationToolbarBinding;
import com.vladimirkondenko.yamblz.model.entities.Languages;
import com.vladimirkondenko.yamblz.screens.ScreenCodes;
import com.vladimirkondenko.yamblz.screens.history.HistoryFragment;
import com.vladimirkondenko.yamblz.screens.translation.TranslationFragment;
import com.vladimirkondenko.yamblz.utils.AnimUtils;
import com.vladimirkondenko.yamblz.utils.LanguageSpinnerAdapter;
import com.vladimirkondenko.yamblz.utils.Utils;
import com.vladimirkondenko.yamblz.utils.events.Bus;
import com.vladimirkondenko.yamblz.utils.events.InputLanguageSelectionEvent;
import com.vladimirkondenko.yamblz.utils.events.OutputLanguageSelectionEvent;
import com.vladimirkondenko.yamblz.utils.events.SelectLanguageEvent;
import com.vladimirkondenko.yamblz.utils.events.SwapLanguageEvent;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import javax.inject.Inject;

import io.reactivex.disposables.Disposable;
import io.realm.Realm;

public class MainActivity extends AppCompatActivity implements MainView {

    @Inject
    public MainPresenter presenter;

    @Inject
    public TranslationFragment translationFragment;
    @Inject
    public HistoryFragment historyFragment;

    private int currentFragment = ScreenCodes.Translation.SCREEN_ID;

    private ActivityMainBinding binding;

    private Spinner spinnerInputLangs;

    private Spinner spinnerOutputLangs;
    private LanguageSpinnerAdapter adapterInputLangs;

    private LanguageSpinnerAdapter adapterOutputLangs;
    private Disposable inputSpinnerSubscription;

    private Disposable outputSpinnerSubscription;
    private Disposable swapButtonSubscription;

    @Inject
    public Realm realm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.get().plusMainSubcomponent(new MainPresenterModule(this)).inject(this);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.bottomnavMain.setOnNavigationItemSelectedListener(item -> {
            presenter.selectScreen(ScreenCodes.menuItemToScreenId(item.getItemId()));
            return false;
        });
        // Set initial screen
        if (savedInstanceState != null) currentFragment = savedInstanceState.getInt(Const.BUNDLE_SELECTED_FRAGMENT);
        setupCustomToolbar();
        presenter.selectScreen(currentFragment);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Bus.subscribe(this);
        presenter.attachView(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        presenter.onPause();
        presenter.detachView();
        Bus.unsubscribe(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Utils.disposeAll(inputSpinnerSubscription, outputSpinnerSubscription, swapButtonSubscription);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        App.get().clearMainPresenterComponent();
        if (realm != null && !realm.isClosed()) realm.close();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(Const.BUNDLE_SELECTED_FRAGMENT, currentFragment);
    }

    @Override
    public void onSelectTranslationScreen() {
        currentFragment = ScreenCodes.Translation.SCREEN_ID;
        setFragment(translationFragment);
        getSupportActionBar().setDisplayShowCustomEnabled(true);
    }

    @Override
    public void onSelectHistoryScreen() {
        currentFragment = ScreenCodes.History.SCREEN_ID;
        setFragment(historyFragment);
        getSupportActionBar().setDisplayShowCustomEnabled(false);
    }

    @Override
    public void onSelectBookmarksScreen() {
        Toast.makeText(this, "To be implemented", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSelectInputLang(String lang) {
        spinnerInputLangs.setSelection(adapterInputLangs.getItemPosition(lang));
    }

    @Override
    public void onSelectOutputLang(String lang) {
        spinnerOutputLangs.setSelection(adapterOutputLangs.getItemPosition(lang));
    }

    @Override
    public void onLoadLanguages(Languages langs) {
        if (adapterInputLangs != null && adapterOutputLangs != null) {
            adapterInputLangs.setLangs(langs, true);
            adapterOutputLangs.setLangs(langs);
            presenter.getSelectedLanguages(langs);
        }
    }

    @Override
    public void onError(Throwable error) {
        if (error != null) {
            error.printStackTrace();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSelectDetectedLanguage(SelectLanguageEvent event) {
        spinnerInputLangs.setSelection(adapterInputLangs.getItemPosition(event.getSelectedLang()), true);
    }

    private void setFragment(Fragment fragment) {
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        transaction.replace(R.id.framelayout_main_container, fragment);
        transaction.commit();
    }

    private void setupCustomToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_main);
        setSupportActionBar(toolbar);
        // Toolbar layout
        ActionBar supportActionBar = getSupportActionBar();
        LayoutTranslationToolbarBinding toolbarBinding = DataBindingUtil.inflate(
                getLayoutInflater(),
                R.layout.layout_translation_toolbar,
                binding.relativelayoutMainRoot,
                false
        );
        supportActionBar.setCustomView(toolbarBinding.relativelayoutTranslationToolbarRoot, new ActionBar.LayoutParams(ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.MATCH_PARENT));
        // Views
        ImageButton buttonSwapLanguage = toolbarBinding.buttonTranslationSwitchLanguage;
        buttonSwapLanguage.setImageDrawable(Utils.getTintedDrawable(this, R.drawable.ic_switch_language_black_24dp, R.color.all_icon_statelist));
        spinnerInputLangs = toolbarBinding.spinnerTranslationLangInput;
        spinnerOutputLangs = toolbarBinding.spinnerTranslationLangTranslation;
        // Adapters
        adapterInputLangs = new LanguageSpinnerAdapter(this);
        adapterOutputLangs = new LanguageSpinnerAdapter(this);
        spinnerInputLangs.setAdapter(adapterInputLangs);
        spinnerOutputLangs.setAdapter(adapterOutputLangs);
        // Reactive event listeners
        inputSpinnerSubscription = RxAdapterView
                .itemSelections(toolbarBinding.spinnerTranslationLangInput)
                .subscribe(position -> {
                    String language = adapterInputLangs.getItem(position);
                    // Disable the swap button if the language is not specified
                    toolbarBinding.buttonTranslationSwitchLanguage.setEnabled(!language.equals(Const.LANG_CODE_AUTO));
                    Bus.post(new InputLanguageSelectionEvent(language));
                    presenter.setInputLang(language);
                });
        outputSpinnerSubscription = RxAdapterView
                .itemSelections(toolbarBinding.spinnerTranslationLangTranslation)
                .subscribe(position -> {
                    String language = adapterOutputLangs.getItem(position);
                    Bus.post(new OutputLanguageSelectionEvent(language));
                    presenter.setOutputLang(language);
                });
        // Language swapping button
        swapButtonSubscription = RxView.clicks(buttonSwapLanguage)
                .subscribe(event -> {
                    Bus.post(new SwapLanguageEvent());
                    int animDistance = 4;
                    int animDuration = Const.ANIM_DURATION_LANG_SWITCH_SPINNER;
                    // +/- 1's are used because of the "Detect language" item
                    // We have to shift the position to get the actual selected language
                    int currentInputPosition = spinnerInputLangs.getSelectedItemPosition();
                    int currentOutputPosition = spinnerOutputLangs.getSelectedItemPosition();
                    toolbarBinding.buttonTranslationSwitchLanguage.animate()
                            .rotationBy(180)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .setDuration(Const.ANIM_DURATION_DEFAULT);
                    AnimUtils.slideInAndOut(
                            spinnerInputLangs,
                            true,
                            animDistance,
                            animDuration,
                            () -> buttonSwapLanguage.setClickable(false), // Disable button clicks to prevent multiple animations from being executed
                            () -> {
                                spinnerInputLangs.setSelection(currentOutputPosition + 1);
                                buttonSwapLanguage.setClickable(true); // Enable button clicks when the animation ends
                            }
                    );
                    AnimUtils.slideInAndOut(
                            spinnerOutputLangs,
                            false,
                            animDistance,
                            animDuration,
                            () -> {
                            },
                            () -> spinnerOutputLangs.setSelection(currentInputPosition - 1)
                    );
                });
        presenter.getLanguages();
    }


}

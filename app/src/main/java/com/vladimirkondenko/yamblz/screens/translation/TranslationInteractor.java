package com.vladimirkondenko.yamblz.screens.translation;

import com.vladimirkondenko.yamblz.Const;
import com.vladimirkondenko.yamblz.model.entities.DetectedLanguage;
import com.vladimirkondenko.yamblz.model.entities.Translation;
import com.vladimirkondenko.yamblz.model.services.DbSavedTranslationsService;
import com.vladimirkondenko.yamblz.model.services.NetTranslationService;
import com.vladimirkondenko.yamblz.utils.LanguageUtils;
import com.vladimirkondenko.yamblz.utils.Utils;
import com.vladimirkondenko.yamblz.utils.base.BaseInteractor;
import com.vladimirkondenko.yamblz.utils.events.Bus;
import com.vladimirkondenko.yamblz.utils.events.LanguageDetectionEvent;

import javax.inject.Inject;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class TranslationInteractor extends BaseInteractor {

    private NetTranslationService netService;
    private DbSavedTranslationsService dbService;

    @Inject
    public TranslationInteractor(NetTranslationService netService, DbSavedTranslationsService dbService) {
        this.netService = netService;
        this.dbService = dbService;
    }

    public Single<Translation> translate(String inputLang, String targetLang, String inputText) {
        boolean shouldDetectLanguage = inputLang == null || inputLang.equals(Const.LANG_CODE_AUTO);
        String direction = shouldDetectLanguage ? targetLang : LanguageUtils.langsToDirection(inputLang, targetLang);
        return Single.defer(() ->
                         dbService.getIfSaved(inputText, direction)
                        .subscribeOn(Schedulers.newThread())
                        .onErrorResumeNext(netService.getTranslation(inputText, direction))
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnSuccess(translation -> {
                            dbService.setInputText(translation, inputText);
                            if (shouldDetectLanguage) Bus.post(new LanguageDetectionEvent(translation.getDirection()));
                        })
        );
    }

    private Single<DetectedLanguage> detect(String text) {
        return netService.detectLanguage(text)
                .doOnSuccess(language -> Bus.post(new LanguageDetectionEvent(language.lang)));

    }

    public void saveToHistory(Translation translation) {
        long timestamp = Utils.getCurrentTimeSec();
        dbService.setSavedToHistory(translation, true, timestamp);
    }

    public void setBookmakred(Translation translation, boolean bookmarked) {
        long timestamp = Utils.getCurrentTimeSec();
        dbService.setBookmarked(translation, bookmarked, timestamp);
    }

}

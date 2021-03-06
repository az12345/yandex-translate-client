package com.vladimirkondenko.yamblz.model.database;

import android.util.Log;

import com.vladimirkondenko.yamblz.model.entities.Translation;
import com.vladimirkondenko.yamblz.model.services.DbSavedTranslationsService;

import io.reactivex.Single;
import io.realm.RealmResults;

import static android.content.ContentValues.TAG;

public class DbSavedTranslationsServiceImpl extends DatabaseUserClass implements DbSavedTranslationsService {

    public DbSavedTranslationsServiceImpl(Database database) {
        super(database);
    }

    @Override
    public Single<Translation> getIfSaved(String inputText, String direction) {
        Translation existingTranslation = realm.where(Translation.class)
                .equalTo(Translation.FIELD_NAME_INPUT_TEXT, inputText)
                .equalTo(Translation.FIELD_NAME_DIRECTION, direction)
                .findFirst();
        if (existingTranslation != null) {
            Log.i(TAG, "getIfSaved: translation found in database");
            return Single.just(existingTranslation);
        }
        Log.i(TAG, "getIfSaved: not found in database, returning error");
        return Single.error(new NullPointerException("Translation not saved in database"));
    }

    public void setInputText(Translation translation, String inputText) {
        performTransaction(() -> translation.setInputText(inputText));
    }

    public void setSavedToHistory(Translation translation, boolean saveToHistory, long timestamp) {
        performTransaction(() -> {
            if (translation.isBookmarked() && !saveToHistory) {
                if (translation.isManaged()) delete(translation);
            } else {
                if (translation.getId() == 0) translation.setId(translation.calculateId());
                translation.setTimestamp(timestamp);
                translation.setSavedToHistory(saveToHistory);
                realm.copyToRealmOrUpdate(translation);
            }
        });
    }

    public void setBookmarked(Translation translation, boolean bookmarked, long timestamp) {
        performTransaction(() -> {
            if (!translation.isSavedToHistory() && !bookmarked) {
                if (translation.isManaged()) delete(translation);
            } else {
                if (translation.getId() == 0) translation.setId(translation.calculateId());
                translation.setTimestamp(timestamp);
                translation.setBookmarked(bookmarked);
                realm.copyToRealmOrUpdate(translation);
            }
        });
    }

    @Override
    public RealmResults<Translation> getHistory() {
        return realm.where(Translation.class)
                .equalTo(Translation.FIELD_NAME_SAVED_TO_HISTORY, true)
                .findAllSortedAsync(Translation.FIELD_NAME_TIMESTAMP);
    }

    @Override
    public RealmResults<Translation> getBookmarks() {
        return realm.where(Translation.class)
                .equalTo(Translation.FIELD_NAME_BOOKMARKED, true)
                .findAllSortedAsync(Translation.FIELD_NAME_TIMESTAMP);
    }


}

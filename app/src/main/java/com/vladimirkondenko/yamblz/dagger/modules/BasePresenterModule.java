package com.vladimirkondenko.yamblz.dagger.modules;


import com.vladimirkondenko.yamblz.utils.base.BaseView;

public abstract class BasePresenterModule<T extends BaseView> {

    protected T view;

    protected BasePresenterModule(T view) {
        this.view = view;
    }

}
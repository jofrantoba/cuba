/*
 * Copyright (c) 2008-2017 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.cuba.web.gui.components;

import com.haulmont.chile.core.model.MetaPropertyPath;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.UserSessionSource;
import com.haulmont.cuba.gui.components.CaptionMode;
import com.haulmont.cuba.gui.components.OptionsStyleProvider;
import com.haulmont.cuba.gui.components.SecuredActionsHolder;
import com.haulmont.cuba.gui.components.SuggestionPickerField;
import com.haulmont.cuba.gui.executors.BackgroundTask;
import com.haulmont.cuba.gui.executors.BackgroundTaskHandler;
import com.haulmont.cuba.gui.executors.BackgroundWorker;
import com.haulmont.cuba.gui.executors.TaskLifeCycle;
import com.haulmont.cuba.web.gui.components.converters.StringToEntityConverter;
import com.haulmont.cuba.web.widgets.CubaPickerField;
import com.haulmont.cuba.web.widgets.CubaSuggestionPickerField;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WebSuggestionPickerField<V extends Entity> extends WebPickerField<V>
        implements SuggestionPickerField<V>, SecuredActionsHolder {

    private static final Logger log = LoggerFactory.getLogger(WebSuggestionPickerField.class);

    /* Beans */
    protected BackgroundWorker backgroundWorker;

    protected BackgroundTaskHandler<List<V>> handler;

    protected SearchExecutor<V> searchExecutor;

    protected EnterActionHandler enterActionHandler;
    protected ArrowDownActionHandler arrowDownActionHandler;

    protected OptionsStyleProvider optionsStyleProvider;

    protected StringToEntityConverter entityConverter = new StringToEntityConverter();
    protected Locale locale;

    public WebSuggestionPickerField() {
    }

    @Override
    protected CubaPickerField<V> createComponent() {
        return new CubaSuggestionPickerField<>();
    }

    @Override
    public CubaSuggestionPickerField<V> getComponent() {
        //noinspection unchecked
        return (CubaSuggestionPickerField<V>) super.getComponent();
    }

    @Inject
    public void setBackgroundWorker(BackgroundWorker backgroundWorker) {
        this.backgroundWorker = backgroundWorker;
    }

    @Override
    protected void initComponent(CubaPickerField<V> component) {
        getComponent().setTextViewConverter(this::convertToTextView);

        getComponent().setSearchExecutor(query -> {
            cancelSearch();
            searchSuggestions(query);
        });

        getComponent().setCancelSearchHandler(this::cancelSearch);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();

        UserSessionSource userSessionSource =
                applicationContext.getBean(UserSessionSource.NAME, UserSessionSource.class);

        this.locale = userSessionSource.getLocale();
    }

    protected String convertToTextView(V value) {
        if (value == null) {
            return StringUtils.EMPTY;
        }

        if (captionMode == CaptionMode.ITEM) {
            return entityConverter.convertToPresentation(value, String.class, locale);
        }

        if (StringUtils.isNotEmpty(captionProperty)) {
            MetaPropertyPath propertyPath = value.getMetaClass().getPropertyPath(captionProperty);
            if (propertyPath == null) {
                throw new IllegalArgumentException(String.format("Can't find property for given caption property: %s", captionProperty));
            }

            return metadataTools.format(value.getValueEx(captionProperty), propertyPath.getMetaProperty());
        }

        log.warn("Using StringToEntityConverter to get entity text presentation. Caption property is not defined " +
                "while caption mode is \"PROPERTY\"");

        return entityConverter.convertToPresentation(value, String.class, locale);

    }

    protected void cancelSearch() {
        if (handler != null) {
            log.debug("Cancel previous search");

            handler.cancel();
            handler = null;
        }
    }

    protected void searchSuggestions(final String query) {
        BackgroundTask<Long, List<V>> task = getSearchSuggestionsTask(query);
        if (task != null) {
            handler = backgroundWorker.handle(task);
            handler.execute();
        }
    }

    protected BackgroundTask<Long, List<V>> getSearchSuggestionsTask(final String query) {
        if (this.searchExecutor == null)
            return null;

        final SearchExecutor<V> currentSearchExecutor = this.searchExecutor;

        Map<String, Object> params;
        if (currentSearchExecutor instanceof ParametrizedSearchExecutor) {
            params = ((ParametrizedSearchExecutor<?>) currentSearchExecutor).getParams();
        } else {
            params = Collections.emptyMap();
        }

        return new BackgroundTask<Long, List<V>>(0) {
            @Override
            public List<V> run(TaskLifeCycle<Long> taskLifeCycle) throws Exception {
                List<V> result;
                try {
                    result = asyncSearch(currentSearchExecutor, query, params);
                } catch (RuntimeException e) {
                    log.error("Error in async search thread", e);

                    result = Collections.emptyList();
                }

                return result;
            }

            @Override
            public void done(List<V> result) {
                log.debug("Search results for '{}'", query);
                handleSearchResult(result);
            }

            @Override
            public void canceled() {
            }

            @Override
            public boolean handleException(Exception ex) {
                log.error("Error in async search thread", ex);
                return true;
            }
        };
    }

    protected List<V> asyncSearch(SearchExecutor<V> searchExecutor, String searchString,
                                  Map<String, Object> params) throws Exception {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        log.debug("Search '{}'", searchString);

        List<V> searchResultItems;
        if (searchExecutor instanceof ParametrizedSearchExecutor) {
            //noinspection unchecked
            ParametrizedSearchExecutor<V> pSearchExecutor = (ParametrizedSearchExecutor<V>) searchExecutor;
            searchResultItems = pSearchExecutor.search(searchString, params);
        } else {
            searchResultItems = searchExecutor.search(searchString, Collections.emptyMap());
        }

        return searchResultItems;
    }

    protected void handleSearchResult(List<V> results) {
        showSuggestions(results);
    }

    @Override
    public void addFieldListener(FieldListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFieldEditable(boolean editable) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public int getAsyncSearchTimeoutMs() {
        return getComponent().getAsyncSearchDelayMs();
    }

    @Override
    @Deprecated
    public void setAsyncSearchTimeoutMs(int asyncSearchTimeoutMs) {
        getComponent().setAsyncSearchDelayMs(asyncSearchTimeoutMs);
    }

    @Override
    public int getAsyncSearchDelayMs() {
        return getComponent().getAsyncSearchDelayMs();
    }

    @Override
    public void setAsyncSearchDelayMs(int asyncSearchDelayMs) {
        getComponent().setAsyncSearchDelayMs(asyncSearchDelayMs);
    }

    @Override
    public SearchExecutor getSearchExecutor() {
        return searchExecutor;
    }

    @Override
    public void setSearchExecutor(SearchExecutor searchExecutor) {
        this.searchExecutor = searchExecutor;
    }

    @Override
    public EnterActionHandler getEnterActionHandler() {
        return enterActionHandler;
    }

    @Override
    public void setEnterActionHandler(EnterActionHandler enterActionHandler) {
        this.enterActionHandler = enterActionHandler;
        getComponent().setEnterActionHandler(enterActionHandler::onEnterKeyPressed);
    }

    @Override
    public ArrowDownActionHandler getArrowDownActionHandler() {
        return arrowDownActionHandler;
    }

    @Override
    public void setArrowDownActionHandler(ArrowDownActionHandler arrowDownActionHandler) {
        this.arrowDownActionHandler = arrowDownActionHandler;
        getComponent().setArrowDownActionHandler(arrowDownActionHandler::onArrowDownKeyPressed);
    }

    @Override
    public int getMinSearchStringLength() {
        return getComponent().getMinSearchStringLength();
    }

    @Override
    public void setMinSearchStringLength(int minSearchStringLength) {
        getComponent().setMinSearchStringLength(minSearchStringLength);
    }

    @Override
    public int getSuggestionsLimit() {
        return getComponent().getSuggestionsLimit();
    }

    @Override
    public void setSuggestionsLimit(int suggestionsLimit) {
        getComponent().setSuggestionsLimit(suggestionsLimit);
    }

    @Override
    public void showSuggestions(List<V> suggestions) {
        getComponent().showSuggestions(suggestions);
    }

    @Override
    public void setPopupWidth(String popupWidth) {
        getComponent().setPopupWidth(popupWidth);
    }

    @Override
    public String getPopupWidth() {
        return getComponent().getPopupWidth();
    }

    @Override
    public String getInputPrompt() {
        return getComponent().getInputPrompt();
    }

    @Override
    public void setInputPrompt(String inputPrompt) {
        getComponent().setInputPrompt(inputPrompt);
    }

    @Override
    public void setOptionsStyleProvider(OptionsStyleProvider optionsStyleProvider) {
        this.optionsStyleProvider = optionsStyleProvider;

        if (optionsStyleProvider != null) {
            getComponent().setOptionsStyleProvider(item ->
                    optionsStyleProvider.getItemStyleName(this, item));
        } else {
            getComponent().setOptionsStyleProvider(null);
        }
    }

    @Override
    public OptionsStyleProvider getOptionsStyleProvider() {
        return optionsStyleProvider;
    }

    @Override
    public void setStyleName(String name) {
        super.setStyleName(name);

        getComponent().setPopupStyleName(name);
    }

    @Override
    public void addStyleName(String styleName) {
        super.addStyleName(styleName);

        getComponent().addPopupStyleName(styleName);
    }

    @Override
    public void removeStyleName(String styleName) {
        super.removeStyleName(styleName);

        getComponent().removePopupStyleName(styleName);
    }

    @Override
    public void commit() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void discard() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isBuffered() {
        return false;
    }

    @Override
    public void setBuffered(boolean buffered) {
        throw new UnsupportedOperationException("Buffered mode isn't supported");
    }

    @Override
    public boolean isModified() {
        throw new UnsupportedOperationException();
    }
}

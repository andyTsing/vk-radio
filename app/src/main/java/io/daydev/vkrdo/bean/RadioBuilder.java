package io.daydev.vkrdo.bean;

import io.daydev.vkrdo.MediaEvent;

/**
 * Created by dmitry on 23.02.15.
 */
public class RadioBuilder {

    public static RadioBuilder getInstance() {
        return new RadioBuilder();
    }

    private RadioBuilder() {
    }

    private String title;
    private String genre;
    private String mood;
    private String artist;
    private RadioInfo.ArtistLinkType artistLinkType;
    private Integer yearFrom;
    private Integer yearTo;


    public RadioBuilder setTitle(String title) {
        this.title = title;
        return this;
    }

    public RadioBuilder setGenre(String genre) {
        this.genre = genre;
        return this;
    }

    public RadioBuilder setMood(String mood) {
        this.mood = mood;
        return this;
    }

    public RadioBuilder setArtist(String artist) {
        this.artist = artist;
        return this;
    }

    public void setYearFrom(int yearFrom) {
        this.yearFrom = yearFrom;
    }

    public void setYearTo(int yearTo) {
        this.yearTo = yearTo;
    }

    public RadioBuilder setArtistLinkType(RadioInfo.ArtistLinkType artistLinkType) {
        this.artistLinkType = artistLinkType;
        return this;
    }

    public RadioInfo build(){
        return new RadioInfo(title, genre, mood, artist, artistLinkType, yearFrom, yearTo);
    }

    public static RadioInfo clone(RadioInfo radioInfo) {
        return radioInfo == null ? null : new RadioInfo(radioInfo.getTitle(),
                                                        radioInfo.getGenre(),
                                                        radioInfo.getMood(),
                                                        radioInfo.getArtist(),
                                                        radioInfo.getArtistLinkType(),
                                                        radioInfo.getYearFrom(),
                                                        radioInfo.getYearTo());
    }

    public static RadioInfo buildDefault(){
        return getInstance().setArtist(MediaEvent.DEFAULT_HOME_RADIO).setArtistLinkType(RadioInfo.ArtistLinkType.LIMIT).build();
    }
}

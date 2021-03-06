package io.daydev.vkrdo.external.echo;

import android.os.AsyncTask;
import android.util.Log;
import com.echonest.api.v4.EchoNestAPI;
import com.echonest.api.v4.EchoNestException;
import com.echonest.api.v4.PlaylistParams;
import io.daydev.vkrdo.bean.RadioInfo;
import io.daydev.vkrdo.external.ConfigurationHolder;
import io.daydev.vkrdo.external.PlaylistSuplier;
import io.daydev.vkrdo.util.Callback;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dmitry on 06.03.15.
 */
public abstract class AbstractEchoService implements PlaylistSuplier {

    protected static final String TAG = "ECHO";

    protected volatile EchoNestAPI echoNest;

    public void openSessionAsync(final RadioInfo radioInfo, final Callback<RadioInfo> callback) {
        if (echoNest == null) {
            synchronized (this) {
                if (echoNest == null) {
                    echoNest = new EchoNestAPI(ConfigurationHolder.getInstance().getEchoKey());
                    echoNest.setTraceSends(false);
                }
            }
        }

        try {
            AsyncTask<PlaylistParamsWrapper, String, RadioInfo> task = new AsyncTask<PlaylistParamsWrapper, String, RadioInfo>() {
                @Override
                protected RadioInfo doInBackground(PlaylistParamsWrapper... args) {
                    try {
                        return initPlayList (radioInfo, args[0]);
                    } catch (Exception e) {
                        Log.e(TAG, "openSessionAsync", e);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(RadioInfo s) {
                    callback.callback(s);
                }
            };
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, generateParams(radioInfo));
        } catch (Exception e) {
            Log.e(TAG, "openSessionAsync", e);
        }
    }

    protected abstract RadioInfo initPlayList(RadioInfo radioInfo, PlaylistParamsWrapper params) throws EchoNestException;

    protected PlaylistParamsWrapper generateParams(RadioInfo radioInfo) {
        PlaylistParams params = new PlaylistParams();
        List<String> extraArtists = new ArrayList<>();

        params.includeAudioSummary();
        if (radioInfo.isEmpty()) {
            params.addGenre("pop");
            params.setType(PlaylistParams.PlaylistType.GENRE_RADIO);
        } else {
            String artist = radioInfo.getArtist();
            if (artist != null && !artist.isEmpty()) {

                if (artist.contains(",") ){
                    String[] artists = artist.split(",");
                    for (String artistTmp : artists) {
                        extraArtists.add(artistTmp.trim());
                    }
                } else {
                    params.addArtist(artist);
                }

                if (radioInfo.getMood() != null && !radioInfo.getMood().isEmpty()) {
                    params.addMood(radioInfo.getMood());
                }

                if (radioInfo.getArtistLinkType() != null) {
                    if (radioInfo.getArtistLinkType().equals(RadioInfo.ArtistLinkType.LIMIT)) {
                        params.setType(PlaylistParams.PlaylistType.ARTIST);
                    } else {
                        params.setType(PlaylistParams.PlaylistType.ARTIST_RADIO);
                    }
                }
            } else if (radioInfo.getGenre() != null && !radioInfo.getGenre().isEmpty()) {
                params.addGenre(radioInfo.getGenre());
                params.setType(PlaylistParams.PlaylistType.GENRE_RADIO);
            }

            if (radioInfo.getYearFrom() != null) {
                params.setArtistStartYearAfter(radioInfo.getYearFrom());
            }

            if (radioInfo.getYearTo() != null) {
                params.setArtistEndYearBefore(radioInfo.getYearTo());
            }
        }
        return new PlaylistParamsWrapper(params, extraArtists);
    }


}

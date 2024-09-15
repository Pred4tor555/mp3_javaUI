import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;

import java.io.*;
import java.util.ArrayList;

public class MusicPlayerLogic extends PlaybackListener {
    // this will be used to update isPaused more synchronously
    private static final Object playSignal = new Object();

    // need reference so that we can update the gui in this class
    private MusicPlayerGUI musicPlayerGUI;

    // store song's details
    private Song currentSong;
    public Song getCurrentSong() {
        return currentSong;
    }

    private ArrayList<Song> playlist;

    // we will need to keep track the index we are in the playlist
    private int currentPlaylistSongIndex;

    // use JLayer library to create an AdvancedPlayer obj which will handle playing the music
    private AdvancedPlayer advancedPlayer;

    // pause boolean flag used to indicate whether the player has been paused
    private boolean isPaused;

    // boolean flag used to tell when the song has finished
    private boolean songFinished;

    // stores in last frame when the playback is finished (used for pausing and resuming)
    private int currentFrame;
    public void setCurrentFrame(int frame) {
        currentFrame = frame;
    }

    // track how many milliseconds has passed since playing the song (used for updating the slider)
    private int currentTimeInMilli;
    public void setCurrentTimeInMilli(int timeInMilli) {
        currentTimeInMilli = timeInMilli;
    }

    // constructor
    public MusicPlayerLogic(MusicPlayerGUI musicPlayerGUI) {
        this.musicPlayerGUI = musicPlayerGUI;
    }

    public void loadSong(Song song) {
        currentSong = song;

        // play the current song if not null
        if(currentSong != null) {
            playCurrentSong();
        }
    }

    public void loadPlaylist(File playlistFile) {
        playlist = new ArrayList<>();

        // store the paths from the text file into the playlist array list
        try {
            FileReader fileReader = new FileReader(playlistFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            // reach each line from the text file and store the text into the songPath variable
            String songPath;
            while ((songPath = bufferedReader.readLine()) != null) {
                // create song object based on song path
                Song song = new Song(songPath);

                // add to playlist array list
                playlist.add(song);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(playlist.size() > 0) {
            // reset playback slider
            musicPlayerGUI.setPlaybackSliderValue(0);
            currentTimeInMilli = 0;

            // update current song to the first song in the playlist
            currentSong = playlist.get(0);

            // start from the beginning frame
            currentFrame = 0;

            // update gui
            musicPlayerGUI.enablePauseButtonDisablePlayButton();
            musicPlayerGUI.updateSongTitleAndArtist(currentSong);
            musicPlayerGUI.updatePlaybackSlider(currentSong);

            // start song
            playCurrentSong();
        }
    }

    public void pauseSong() {
        if (advancedPlayer != null) {
            isPaused = true;
            stopSong();
        }
    }

    public void stopSong() {
        if (advancedPlayer != null) {
            advancedPlayer.stop();
            advancedPlayer.close();
            advancedPlayer = null;
        }
    }

    public void nextSong() {
        // no need to go to the next song if there is no playlist
        if (playlist == null) return;

        // check to see if we have reached the end of the playlist, if so then don't do anything
        if(currentPlaylistSongIndex + 1 > playlist.size() - 1) return;

        // stop the song if possible
        if (!songFinished)
            stopSong();

        // increase current playlist song index
        currentPlaylistSongIndex++;

        // update current song
        currentSong = playlist.get(currentPlaylistSongIndex);

        // reset frame
        currentFrame = 0;

        // reset current time in milli
        currentTimeInMilli = 0;

        // update gui
        musicPlayerGUI.enablePauseButtonDisablePlayButton();
        musicPlayerGUI.updateSongTitleAndArtist(currentSong);
        musicPlayerGUI.updatePlaybackSlider(currentSong);

        // play the song
        playCurrentSong();
    }

    public void prevSong() {
        // no need to go to the next song if there is no playlist
        if (playlist == null) return;

        // check to see if we can go to the previous song
        if(currentPlaylistSongIndex - 1 < 0) return;

        // stop the song if possible
        if (!songFinished)
            stopSong();

        // decrease current playlist song index
        currentPlaylistSongIndex--;

        // update current song
        currentSong = playlist.get(currentPlaylistSongIndex);

        // reset frame
        currentFrame = 0;

        // reset current time in milli
        currentTimeInMilli = 0;

        // update gui
        musicPlayerGUI.enablePauseButtonDisablePlayButton();
        musicPlayerGUI.updateSongTitleAndArtist(currentSong);
        musicPlayerGUI.updatePlaybackSlider(currentSong);

        // play the song
        playCurrentSong();
    }

    public void playCurrentSong() {
        if(currentSong == null) return;

        try {
            // read mp3 audio data
            FileInputStream fileInputStream = new FileInputStream(currentSong.getFilePath());
            BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);

            // create a new advanced player
            advancedPlayer = new AdvancedPlayer(bufferedInputStream);
            advancedPlayer.setPlayBackListener(this);

            // start music
            startMusicThread();

            // start playback slider thread
            startPlaybackSliderThread();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startMusicThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(isPaused) {
                        synchronized (playSignal) {
                            // update flag
                            isPaused = false;

                            // notify the other thread to continue (makes sure that isPaused is updated to false properly)
                            playSignal.notify();
                        }

                        // resume music from last frame
                        advancedPlayer.play(currentFrame, Integer.MAX_VALUE);
                    } else {
                        // play music from the beginning
                        advancedPlayer.play();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // create a thread that will handle updating the slider
    private void startPlaybackSliderThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (isPaused) {
                    try {
                        // wait till it gets notified by other thread to continue
                        // makes sure that isPaused boolean flag updates to false before continuing
                        synchronized (playSignal) {
                            playSignal.wait();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                while (!isPaused) {
                    try {
                        // increment current time milli
                        currentTimeInMilli++;

                        // calculate into frame value
                        int calculatedFrame = (int) ((double) currentTimeInMilli * 2.08 * currentSong.getFrameRatePerMilliseconds());

                        // update GUI
                        musicPlayerGUI.setPlaybackSliderValue(calculatedFrame);

                        // mimic 1 millisecond using thread.sleep
                        Thread.sleep(1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    @Override
    public void playbackStarted(PlaybackEvent evt) {
        // this method gets called in the beginning of the song
        System.out.println("Playback Started");
        songFinished = false;
    }

    @Override
    public void playbackFinished(PlaybackEvent evt) {
        // this method gets called when the song finishes or if the player gets closed
        System.out.println("Playback Finished");

        if(isPaused) {
            currentFrame += (int) ((double) evt.getFrame() * currentSong.getFrameRatePerMilliseconds());
        } else {
            // when the song ends
            songFinished = true;

            // empty playlist
            if (playlist == null) {
                // update gui
                musicPlayerGUI.enablePlayButtonDisablePauseButton();
            } else {
                // last song in the playlist
                if (currentPlaylistSongIndex == playlist.size() - 1) {
                    // update gui
                    musicPlayerGUI.enablePlayButtonDisablePauseButton();
                } else {
                    // go to the next song in the playlist
                    nextSong();
                }
            }
        }
    }
}
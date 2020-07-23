package org.sdci.sdk.service;

import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;

public class DASHStreamClient implements IStreamClient {
	volatile boolean error = false;
	private Object lock = new Object();

	public int read(String source, String destination) {
		String[] options = { ":sout=#standard{access=file,dst=" + destination + "}", "--network-caching=15000", };
		MediaPlayerFactory factory = new MediaPlayerFactory();
		MediaPlayer mediaPlayer = factory.mediaPlayers().newMediaPlayer();
		mediaPlayer.media().play("http://" + source, options);
		mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
			@Override
			public void finished(MediaPlayer mediaPlayer) {
				synchronized (lock) {
					System.out.println("finished reading");
					lock.notify();
					mediaPlayer.release();
				}
			}

			@Override
			public void error(MediaPlayer mediaPlayer) {
				synchronized (lock) {
					error = true;
					lock.notify();
					mediaPlayer.release();
				}

			}
		});

		synchronized (lock) {
			System.out.println("waiting");
			try {
				lock.wait();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("finished waiting");
		}

		System.out.println("error : " + error);
		if (error) {
			return -1;
		}
		return 0;
	}

}

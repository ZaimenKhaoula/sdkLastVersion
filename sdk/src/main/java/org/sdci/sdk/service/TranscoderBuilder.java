package org.sdci.sdk.service;

import java.util.HashMap;
import java.util.Set;
import java.util.Map.Entry;

import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;

public class TranscoderBuilder {

	private String input;
	private String output;
	private HashMap<String, String> transcoderParams = new HashMap<String, String>();
	volatile boolean error = false;
	private Object lock = new Object();

	public TranscoderBuilder(String input, String output) {
		this.input = input;
		this.output = output;
	}

	public void setAudioCodec(String codec) {
		transcoderParams.put("acodec", codec);
	}

	public void setVideoCodec(String codec) {
		transcoderParams.put("vcodec", codec);
	}

	public void setVideoBitrate(String bitrate) {
		transcoderParams.put("vb", bitrate);
	}

	public void setAudioBitrate(String bitrate) {
		transcoderParams.put("ab", bitrate);
	}

	public void setVideoResolution(String width, String height) {
		transcoderParams.put("vfilter", "canvas{width=" + width + ",height=" + height + "}");
	}

	public int execute() {
		String transcode = "";
		Set<Entry<String, String>> entries = transcoderParams.entrySet();
		for (Entry<String, String> entry : entries) {
			transcode = transcode.concat(entry.getKey() + "=" + entry.getValue() + ",");
		}
		transcode = transcode.substring(0, transcode.length() - 1);

		if (!transcode.isEmpty()) {
			String[] options = { ":sout=#transcode{" + transcode + "}:file{dst=" + output + "}" };
			System.out.println("Options: " + options[0]);
			MediaPlayerFactory factory = new MediaPlayerFactory();
			MediaPlayer mediaPlayer = factory.mediaPlayers().newMediaPlayer();
			mediaPlayer.media().play(input, options);
			mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
				@Override
				public void finished(MediaPlayer mediaPlayer) {
					synchronized (lock) {
						System.out.println("finished");
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
				System.out.println("error : " + error);
			}
			if (error) {
				return -1;
			}

		} else {
			System.out.println("No Transcode parameters entered");
			return -1;
		}
		return 0;

	}
}

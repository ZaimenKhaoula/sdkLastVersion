package org.sdci.sdk.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public class DASHStreamServer implements IStreamServer {

	public String stream(String[] input) {
		try {
			
			ArrayList<String> cmd = new ArrayList<String>();
			cmd.add("MP4Box");
			cmd.add("-dash");
			cmd.add("10000");
			cmd.add("-frag");
			cmd.add("1000");
			cmd.add("-rap");
			cmd.add("-segment-name");
			cmd.add("segment_%s");
			cmd.add("-out");
			cmd.add(input[0]+".mpd");
			cmd.addAll(Arrays.asList(input));

			ProcessBuilder processBuilder = new ProcessBuilder(cmd);
			processBuilder.inheritIO();
			Process process = processBuilder.start();
			int exitCode = process.waitFor();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		return input[0]+".mpd";
	}

	private static String readInputStream(InputStream inputStream) throws IOException {
		return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
	}

}

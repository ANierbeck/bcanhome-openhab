/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2013, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */
package org.openhab.io.multimedia.internal.tts;

import java.io.IOException;

import org.openhab.io.multimedia.tts.TTSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a TTS service implementation for MacOS, which simply uses the "say" command from MacOS.
 * 
 * @author Kai Kreuzer
 * @since 0.8.0
 *
 */
public class TTSServiceMacOS implements TTSService {

	private static final Logger logger = LoggerFactory.getLogger(TTSServiceMacOS.class);
	
	/**
	 * {@inheritDoc}
	 */
	public synchronized void say(String text, String voiceName) {

		String command = "say " + (voiceName!=null ? " -v " + voiceName + " " : "") + text.replace("-", " minus ");  
		try {
			Process process = Runtime.getRuntime().exec(command);
			process.waitFor();
		} catch (IOException e) {
			logger.error("Error while executing the 'say' command: " + e.getMessage());
		} catch (InterruptedException e) {
			logger.error("The 'say' command has been interrupted: " + e.getMessage());
		}
	}

}

/*******************************************************************************
 * Copyright  2015 rzorzorzo@users.sf.net
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
 *******************************************************************************/
package org.rzo.yajsw.os.posix;

import java.io.File;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.CompositeConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.rzo.yajsw.os.JavaHome;

import io.netty.util.internal.logging.InternalLogger;

public class PosixJavaHome implements JavaHome
{
	Configuration _config;
	InternalLogger _logger;
	int _debug = 3;

	public PosixJavaHome(Configuration config)
	{
		if (config != null)
			_config = config;
		else
			_config = new BaseConfiguration();
	}

	public String findJava(String wrapperJava, String customProcessName)
	{
		File customProc = null;
		File wrapJava = null;

		// Search for JAVA if necessary ( nothing supplied )
		if (wrapperJava == null && customProcessName == null)
			return findJava();

		customProc = ((customProcessName != null) ? new File(customProcessName)
				: null);
		wrapJava = ((wrapperJava != null) ? new File(wrapperJava) : null);

		// customProcessName takes precedences over wrapperJava
		if (customProc != null && customProc.exists()
				&& customProc.canExecute())
		{
			return customProcessName;
		}
		else if (wrapJava != null && wrapJava.exists() && wrapJava.canExecute())
		{
			return wrapperJava;
		}
		else
			return findJava();
		// -old return wrapperJava == null ? "java" : wrapperJava;
	}

	private String findJava()
	{
		// Posix Version does not use wrapper.java.command like Win version
		// does. ( whatever )
		// Find working java and equate to both
		File fJava = null;
		String java = null;

		// Find Path to Regular Java
		String javaFiles[] = new String[3];
		javaFiles[0] = _config.getString("wrapper.java.command");
		javaFiles[1] = _config.getString("wrapper.ntservice.java.command");
		javaFiles[2] = "java";

		for (int idx = 0; (fJava == null && idx < javaFiles.length); idx++)
		{
			String javaName;
			for (int loop = 0; loop < 2; loop++)
			{
				if (javaFiles[idx] != null)
				{
					javaName = ((loop == 0) ? javaFiles[idx] : System
							.getProperty("java.home", "")
							+ File.separator
							+ "bin" + File.separator + javaFiles[idx]);
					File fJavaTmp = new File(javaName);
					//System.out.println(fJavaTmp + "exists "+fJavaTmp.exists());
					//System.out.println(fJavaTmp + "canExecute "+fJavaTmp.canExecute());
					if (fJavaTmp.exists() && fJavaTmp.canExecute())
					{
						fJava = fJavaTmp;
						break;
					}
				}
			}
		}

		// if Regular java not found.... Search Path for JAVA's HOME
		if (fJava == null)
		{
			System.out.println("regular not found");
			// Check path for JAVA's HOME
			String home = findJavaHomeFromPath(null);
			if (home != null)
			{
				String javaName;
				javaName = home + File.separator + "bin" + File.separator
						+ "java";
				File fJavaTmp = new File(javaName);
				//System.out.println(fJavaTmp + "exists "+fJavaTmp.exists());
				//System.out.println(fJavaTmp + "canExecute "+fJavaTmp.canExecute());
				if (fJavaTmp.exists() && fJavaTmp.canExecute())
				{
					fJava = fJavaTmp;
				}
			}
		}

		// if Regular java still not found.... bummer were done
		if (fJava != null)
		{
			//System.out.println("final regular not found");
			java = fJava.getAbsolutePath();

			// Posix Version does not use wrapper.java.command like Win version
			// does. Update both
			((CompositeConfiguration)_config).getConfiguration(0).setProperty("wrapper.java.command", java);
			((CompositeConfiguration)_config).getConfiguration(0).setProperty("wrapper.ntservice.java.command", java);
		}

		if (java == null)
			java = _config.getString("wrapper.java.command", "java");

		return java;
	}

	// Searches Environment Path for JAVA_HOME equivalent
	private String findJavaHomeFromPath(String javaHome)
	{
		if (javaHome != null)
		{
			File fJavaHome = new File(javaHome);
			if (fJavaHome.exists())
				return javaHome;
		}

		// search java in environment path
		if (System.getenv("path") == null)
			return null;
		String[] paths = System.getenv("path").split(File.pathSeparator);
		for (String path : paths)
		{
			if (path.contains("jdk") || path.contains("jre"))
			{
				File fJavaHome = new File(path + File.separator + "java");
				if (fJavaHome.exists())
				{
					return fJavaHome.getParentFile().getParentFile()
							.getAbsolutePath();
				}
			}
		}

		return null;
	}

	public void setLogger(InternalLogger logger, int debug)
	{
		_logger = logger;
		_debug = debug;
	}

}

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
package org.rzo.yajsw.nettyutils;

import java.util.logging.Logger;

import io.netty.handler.logging.LoggingHandler;

public class LoggingFilter extends LoggingHandler
{
	Logger _logger;
	String _name;

	public LoggingFilter(Logger logger, String name)
	{
		_logger = logger;
		_name = name;
	}

	private void log(String txt)
	{
		if (_logger == null)
			System.out.println(txt);
		else
			_logger.fine("[" + _name + "]" + txt);
	}

}

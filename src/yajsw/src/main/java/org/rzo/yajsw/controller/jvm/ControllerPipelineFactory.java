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
package org.rzo.yajsw.controller.jvm;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import org.rzo.yajsw.Constants;
import org.rzo.yajsw.controller.Message;
import org.rzo.yajsw.nettyutils.ChannelGroupFilter;
import org.rzo.yajsw.nettyutils.Condition;
import org.rzo.yajsw.nettyutils.ConditionFilter;
import org.rzo.yajsw.nettyutils.LoggingFilter;
import org.rzo.yajsw.nettyutils.WhitelistFilter;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;

class ControllerPipelineFactory extends ChannelInitializer<SocketChannel>
{

	JVMController _controller;
	boolean _debug = false;

	ControllerPipelineFactory(JVMController controller)
	{
		_controller = controller;
	}

	public void setDebug(boolean debug)
	{
		_debug = debug;
	}

	@Override
	protected void initChannel(SocketChannel ch) throws Exception
	{
		ChannelPipeline pipeline = ch.pipeline(); // Note the static import.
		if (_debug)
			pipeline.addLast("logging1", new LoggingFilter(
					_controller.getLog(), "controller"));

		// allow new connections only if state != LOGGED_ON
		// and only if state != PROCESS_KILLED
		pipeline.addLast("checkWaiting", new ConditionFilter(new Condition()
		{
			public boolean isOk(ChannelHandlerContext ctx, Object msg)
			{
				boolean result = true;
				int currentState = _controller.getState();
				if (currentState == JVMController.STATE_LOGGED_ON)
				{
					_controller
							.getLog()
							.info("app already logged on -> rejecting new connection");
					result = false;
				}
				else if (currentState == JVMController.STATE_PROCESS_KILLED)
				{
					if (_debug)
						_controller.getLog().info(
								"app not running -> rejecting new connection");
					result = false;
				}
				return result;
			}
		}));

		// create a firewall allowing only localhosts to connect
		WhitelistFilter firewall = new WhitelistFilter();
		
		/*
		try
		{
			firewall.allowAll(InetAddress.getAllByName("127.0.0.1"));
		}
		catch (UnknownHostException e)
		{
			_controller.getLog().warning("error getting 127.0.0.1 by name: "+e.getMessage());
		}
		try
		{
			firewall.allow(InetAddress.getLocalHost());
		}
		catch (UnknownHostException e)
		{
			_controller.getLog().warning("error getting localhost by name: "+e.getMessage());
		}
		*/
		
        boolean ipAdded = false;
        try {

            InetAddress localhost = InetAddress.getLocalHost();
            firewall.allow(localhost);
            ipAdded = true;
            if (_debug){
                _controller.getLog().info("Added localhost IP addr: " + localhost + " - " + localhost.getHostAddress());
            }

            //Just in case this host has multiple IP addresses....
            InetAddress[] allIpsForCanonicalHostName = InetAddress.getAllByName(localhost.getCanonicalHostName());
            if (allIpsForCanonicalHostName != null && allIpsForCanonicalHostName.length > 1) {
                if (_debug){
                    _controller.getLog().info("Found multiple network internet addresses for canonical host name:");
                }
                for (int i = 0; i < allIpsForCanonicalHostName.length; i++) {
                    InetAddress currentAddress = allIpsForCanonicalHostName[i];
                    if (_debug){
                        _controller.getLog().info("   Added InetAddress " + currentAddress);
                    }
                    firewall.allow(currentAddress);
                    ipAdded = true;
                }
            }
        } catch (UnknownHostException e) {
            _controller.getLog().throwing(JVMController.class.getName(), "start", e);
        }


        try {

            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface currentInterface = en.nextElement();

                if (_debug){
                    _controller.getLog().info("Adding local IPs for interface " + currentInterface.getName() + " - " + currentInterface.getDisplayName());
                }
                for (Enumeration<InetAddress> enumIpAddr = currentInterface.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress currentAddress = enumIpAddr.nextElement();
                    if (_debug){
                        _controller.getLog().info("   Added InetAddress " + currentAddress.toString());
                    }
                    firewall.allow(currentAddress);
                    ipAdded = true;
                }
            }
        } catch (SocketException e) {
            _controller.getLog().throwing(JVMController.class.getName(), "start", e);
        }

        if(!ipAdded){
            _controller.getLog().info("No IP added to whitelist. Defaulting to allow 127.0.0.1 and 127.0.1.1 .");
            byte[] addressBytes;

            addressBytes = new byte[] { 127, 0, 0, 1 };
            firewall.allow(InetAddress.getByAddress("localhost", addressBytes));

            addressBytes = new byte[] { 127, 0, 1, 1 };
            firewall.allow(InetAddress.getByAddress("localhost", addressBytes));
        }

		pipeline.addLast("firewall", firewall);

		
		// add a framer to split incoming bytes to message chunks
		pipeline.addLast("framer", new DelimiterBasedFrameDecoder(8192, true,
				Delimiters.nulDelimiter()));

		// add messge codec
		pipeline.addLast("messageEncoder", new MessageEncoder());
		pipeline.addLast("messageDecoder", new MessageDecoder());

		if (_debug)
		{
			pipeline.addLast("logging", new LoggingFilter(_controller.getLog(),
					"controller"));
			_controller.getLog().info("jvm controller: netty logger set");
		}

		// if we found our partner close all other open connections
		pipeline.addLast("removeConnected", new ChannelGroupFilter(
				new Condition()
				{
					public boolean isOk(ChannelHandlerContext ctx, Object msg)
					{
						boolean result = false;
						if (msg instanceof Message)
						{
							Message m = (Message) msg;
							result = m.getCode() == Constants.WRAPPER_MSG_OKKEY;
						}
						return result;
					}
				}));

		// at last add the message handler
		pipeline.addLast("handler", new ControllerHandler(_controller));

	}

}

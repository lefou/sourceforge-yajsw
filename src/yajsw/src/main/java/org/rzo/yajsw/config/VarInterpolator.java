package org.rzo.yajsw.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.interpol.ConfigurationInterpolator;
import org.rzo.yajsw.util.MyKeyStoreInterface;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;


public class VarInterpolator extends ConfigurationInterpolator implements YajswConfigurationInterpolator
{
	Configuration	_conf;
	Map				_cache		= null;
	String[]		_imports	= null;
	InternalLogger			log						= InternalLoggerFactory.getInstance(this.getClass().getName());
	Map<String, String>             _fromBinding = new HashMap<String, String>();
	volatile MyKeyStoreInterface _ks = null;
	byte _level = 0;
	private ConfigurationBinding _binding;

	public VarInterpolator(Configuration conf, boolean cache, String[] imports, Map utils)
	{
		_conf = conf;
		_binding = new ConfigurationBinding(conf, utils);
		setCache(cache);
		_imports = imports;
	}

	public VarInterpolator(Configuration conf)
	{
		this(conf, false, null, null);
	}

	public void setCache(boolean cache)
	{
		if (cache)
			_cache = new HashMap();
	}

	public Object interpolate(Object value)
	{
		if (!(value instanceof String))
			return value;
		if (_cache != null)
		{
			Object cachedResult = _cache.get(value);
			if (cachedResult != null)
				return cachedResult;
		}
		String result = (String) value;
		try
		{
		if (result.startsWith("${keystore "))
		{
			String x = result.substring(11);
			x = x.substring(0, x.lastIndexOf('}'));
			return getFromKeystore(x.trim());
		}
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		int i = result.lastIndexOf("${");
		while (i != -1)
		{
			int r = getExpression(result, i + 2);
			String expression = result.substring(i + 2, r);
			_level++;
			String eval = evaluate(expression);
			_level--;
			String p1 = result.substring(0, i);
			String p2 = result.substring(r + 1);
			result = p1 + eval + p2;
			i = result.lastIndexOf("${");
			if (i != -1 && value != null && result != null && value.equals(result))
			{
				result = "?unresolved?cycle";
				i = -1;
			}
		}
		if (_cache != null)
			_cache.put(value, result);
		return result;

	}

	public String getFromKeystore(String key) throws Exception
	{
		MyKeyStoreInterface ks = getKeyStore();
		String result =  new String(ks.get(key));
		return result;
	}

	private MyKeyStoreInterface getKeyStore() throws Exception
	{
		if (_ks == null)
		{
				Class clazz = MyKeyStoreInterface.class.getClassLoader().loadClass("org.rzo.yajsw.util.MyKeyStore");
				_ks = (MyKeyStoreInterface) clazz.newInstance();
			_ks.start();
		}
		return _ks;
	}

	private int getExpression(String value, int i)
	{
		int i1 = value.indexOf('{', i);
		int i2 = value.indexOf('}', i);
		while (i1 != -1 && i2 > i1)
		{
			i2 = value.indexOf('}', i2 + 1);
			i1 = value.indexOf('{', i1 + 1);
		}

		return i2;
	}

	private String evaluate(String value)
	{
		if (_level > 10)
			return "?unresolved?recursion>10?";
		String result = null;
		Exception caught = null;
		try
		{
			result = (String)_binding.getVariable(value);
			if (result != null)
			{
				String result1 = result;
				while (result1 != null && result1.contains("${"))
					result1 = (String) interpolate(result1);
				_fromBinding.put(value, result1);
			}
		}
		catch (Exception ex)
		{
			caught = ex;
			try
			{
			result =  _conf.getString(value);
			}
			catch (Exception ex1)
			{
				caught = ex1;
			}
		}
		if (result == null)
			try
			{
				result = _binding.getVariable(value).toString();
			}
			catch (Exception ex)
			{
				caught = ex;
			}
		if (result == null)
		{
			result = "?unresolved?";
			if (caught != null)
				//log.warn("error evaluating "+value, caught);
				log.warn("error evaluating "+value+" : "+caught.getMessage());
			else
				log.warn("error evaluating "+value);
		}
		return result;

	}
	
	public Map<String, String> getFromBinding()
	{
		return _fromBinding;
	}
	
	public Map<String, String> getUsedEnvVars()
	{
		return _binding.getUsedEnvVars();
	}
	
	public static void main(String[] args) {
		VarInterpolator gi = new VarInterpolator(null);
		Object r = gi.interpolate("${relativeRAM.invoke('calc',15)}");
		System.out.println(r);
	}

	@Override
	public Object getBinding() {
		return _binding;
	}

}

/*
 * MIT License
 * 
 * Copyright (c) 2005-2020 by Anton Kolonin, Aigents®
 * Copyright (c) 2018-2019 SingularityNET
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.webstructor.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

import net.webstructor.al.AL;
import net.webstructor.al.Time;

public class Str {
	
	/**
	 * Get next string element past found one or null
	 * @param args - list of strings
	 * @param name - target element before returned one
	 * @return
	 */
	public static String arg(String[] args, String name, String def){
		int i = Array.index(args, name, false);
		String str = i < 0 || ++i >= args.length ? def : args[i]; 
		return str;
	}
	
	/**
	 * Get next string element past found one or null
	 * @param args - list of strings
	 * @param names - list of alterative target elements before returned one
	 * @return
	 */
	public static String arg(String[] args, String[] names, String def){
		for (int n = 0; n < names.length; n++){
			int i = Array.index(args, names[n], false);
			if (i >= 0)
				return ++i >= args.length ? def : args[i];
		}
		return def;
	}
	
	/**
	 * Check if name is contained by args, case-insensitive
	 * @param args - list of strings
	 * @param name - target element before returned one
	 * @return
	 */
	public static boolean has(String[] args, String name){
		return Array.containsIgnoreCase(args, name);
	}

	/**
	 * Check if next string element past found one is present and matching the expected
	 * @param args - list of strings
	 * @param name - target element before returned one
	 * @param expected - expected element after the taret one
	 * @return true if element is present and either expected is null or element is matching the expected 
	 */
	public static boolean has(String[] args, String name, String expected){
		String val = arg(args, name, null);
		if (val == null || val.length() < 0)
			return false;
		return expected == null || expected.equalsIgnoreCase(val);//any value present or matching expected  
	}

	public static Object[][] get(String[] args, String[] names, Class[] types){
		return get(args, names, types, null);
	}
	
	public static String[] get(String[] args, String name){
		Object[][] objs = Str.get(args,new String[]{name},new Class[]{String.class});
		if (AL.empty(objs))
			return null;
		String[] strs = new String[objs.length];
		int i = 0;
		for (Object o[] : objs)
			strs[i++] = o[0].toString();
		return strs;
	}

	/**
	 * Parse array objects from stream of tokens
	 * @param args - stream of tokens
	 * @param names - names of attributes
	 * @param types - classes of attributes, may be null
	 * @param defaults - default values of attributes, may be null
	 * @return
	 */
	public static Object[][] get(String[] args, String[] names, Class[] types, String[] defaults){
		ArrayList res = new ArrayList();
		for (int i = 0; i < args.length;){
			Object[] o = new Object[names.length];
			int j = 0;
			int next = 0;
			for (; j < names.length; j++){
				int k = Array.index(args, names[j], i, false);
				String value = (k != -1 && (k + 1) < args.length) ? args[k+1] : null;
				if (value == null){
					if (defaults != null && types.length == names.length)
						value = defaults[j];
					else
						break;
				} else
					next = Math.max(next,k + 2);//move to the next item
				if (value != null) {
					o[j] = value;
					if (types != null && types.length == names.length){
						if (types[j] == Integer.class) 
							o[j] = Integer.valueOf(value,10);
						else
						if (types[j] == Double.class) 
							o[j] = Double.valueOf(value);
						else
						if (types[j] == Date.class)
							o[j] = Time.day(value);
					} 
				}
			}
			if (next > 0 && j == names.length){//completed
				res.add(o);
				i = next;
			}else
				break;
		}
		return (Object[][])res.toArray(new Object[][]{});
	}
	
	public static String join(String[] args, String glue){
		StringBuilder sb = new StringBuilder();
		if (args != null)
			for (int i = 0; i < args.length; i++){
				if (args[i] == null || args[i].length() == 0)
					continue;
				if (sb.length() > 0 && glue != null)
					sb.append(glue);
				sb.append(args[i]);
			}
		return sb.toString();
	}

	public static StringBuilder append(StringBuilder builder, String string){
		if (builder != null && string != null && string.length() > 0){
			if (builder.length() > 0)
				builder.append(' ');
			builder.append(string);
		}
		return builder;
	}

	public static String parseBetween(String source, String pre, String post) {
		return parseBetween(source, pre, post, true);
	}
	    
	public static String parseBetween(String source, String pre, String post, boolean aMustPost) {
		if (source != null) {
			int beg = source.indexOf(pre);
			if (beg != -1){
				beg += pre.length();
				if (post == null)
					return source.substring(beg);
				int end = source.indexOf(post, beg);
				if (end != -1)
					return source.substring(beg,end);
				if (!aMustPost)
					return source.substring(beg);
			}
		}
		return null;
	}
	    
	public static String[] concat(String[] first, String[] second){
		String[] result = Arrays.copyOf(first, first.length + second.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}
	
	public static String[] splitName(String full_name,String name,String surname,boolean straight) {
		String[] split = !AL.empty(full_name) ? full_name.split(" ") : null;
		if (AL.empty(name) && !AL.empty(split)){
			if (!straight) {//Doe John Maria => ['Doe Maria', 'John'] (like in PayPal)
				if (split.length < 1)
					name = split[0];
				else {
					surname = split[0];
					name = split[1];
					for (int i = 2; i < split.length; i++)
						name += split[i];
				}
			}else {//John Maria Doe => ['John Maria', 'Doe'] (like normally)
				name = split[0];
				if (split.length > 1) {
					int surname_at_last = split.length - 1;
					for (int i = 1; i < surname_at_last; i++)
						name += split[i];
					surname = split[surname_at_last];
				}
			}
		}
		return new String[] {name,surname};
	}
	
	public static HashSet<String> hset(String[] strings) {
		HashSet<String> set = new HashSet<String>();
		if (strings != null) for (String s : strings)
			set.add(s);
		return set;
	}
	
	public static String last(StringBuilder builder, int n) {
		int l;
		if (builder == null || (l = builder.length()) < n)
			return null;
		l -= 1;
		return builder.substring(l-n, l);
	}

	public static String first(String s, int n) {
		return s == null ? s : s.substring(0, Math.min(s.length(), n));
	}
}

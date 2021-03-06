/*
 * MIT License
 * 
 * Copyright (c) 2005-2019 by Anton Kolonin, Aigents
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
package net.webstructor.cat;

import java.text.DecimalFormat;

class ChunkHolder
{
	int [][] chunks;
	ChunkHolder()
	{
		chunks = null;
	}	
}

public final class StringUtil
{
    public static final String DELIMITERS        = "\\\r\n\t\"\' .,;:()[]{}?!&%$#@+=*-";
    public static final String PATTERNDELIMITERS = "\t\"\'.,;:()[]{}?!";
    public static final String TOKENDELIMITERS   = "\\\r\n &%$#@+=*-";
    public static final String TOKENREGEXP       = "[^0-9.,+]+";
    public static final char MINUS = '−';
    
    //public const String EMAILREGEXP = "\ [^\,^\ ^\@]+\@[^\@^\$]+";

    /*
    static NumberFormatInfo s_formatInfo = null;

    static NumberFormatInfo getFormatInfo()
    {
        if (s_formatInfo==null)
        {
            s_formatInfo = new NumberFormatInfo();
            s_formatInfo.CurrencyDecimalSeparator = ".";
        }
        return s_formatInfo;
    }
    */

    static DecimalFormat fDoubleHigh;
    static DecimalFormat fDoubleLow;
    static DecimalFormat fPercent;
	static 
	{
		fDoubleHigh = new DecimalFormat("#0.000000000");
		java.text.DecimalFormatSymbols dfsHigh = fDoubleHigh.getDecimalFormatSymbols();  
		dfsHigh.setDecimalSeparator('.');
		fDoubleHigh.setDecimalFormatSymbols(dfsHigh);

		fDoubleLow = new DecimalFormat("#0.00");
		java.text.DecimalFormatSymbols dfsLow = fDoubleLow.getDecimalFormatSymbols();  
		dfsLow.setDecimalSeparator('.');
		fDoubleLow.setDecimalFormatSymbols(dfsLow);
		
		fPercent = new DecimalFormat("000");
	}

    static String substring(String s,int from,int len)
    {
        return s.substring(from,from+len);
    }

    static int indexOfAny(String s,char[] chars,int start)
    {
    	int idx = -1;
    	if (chars!=null)
    		for (int i=0;i<chars.length;i++)
    		{
    			int x = s.indexOf(chars[i],start);
    			if (x>=0)
    				if (idx==-1 || x<idx)
    					idx = x;
    		}
    	return idx;
    }
    
    
        //public static int[][] toChunks(String src,String delim)
    	private static ChunkHolder toChunks(String src,String delim)
        {
        	return toChunks(src,delim,false);//table = false by default?
        }
        
        //public static int[][] toChunks(String src,String delim,boolean table)
        public static ChunkHolder toChunks(String src,String delim,boolean table)
        {
        	//int[][] chunks = null;//Java 1.5 explodes with Exception in thread "main" java.lang.VerifyError:  Incompatible types for storing into array of arrays or objects
        	ChunkHolder ch = new ChunkHolder();
            if (src!=null && delim!=null)//20070519
            {
            	char[] delChars = delim.toCharArray();
            	// need two passes
                for (int pass=1;pass<=2;pass++)  
                {
                    int count = 0;
                    int startIndex = 0;
                    int stopIndex;
                    for (;;)
                    {
                        stopIndex = indexOfAny(src,delChars,startIndex);
                        if (stopIndex==startIndex && !table)//in "table" mode, count empty strings 
                            startIndex+=1;
                        else
                        if (startIndex<stopIndex || (table && startIndex==stopIndex)) // stopIndex != -1
                        {
                            if (pass==2)
                            	ch.chunks[count] = new int[] {startIndex,stopIndex-startIndex};
                            count++;  
                            startIndex = stopIndex + 1;
                        }
                        else // stopIndex == -1
                        {
                            int reminder = src.length() - startIndex;
                            if (reminder>0)
                            {
                                if (pass==2)
                                	ch.chunks[count] = new int[] {startIndex,reminder};
                                count++;  
                            }
                            if (pass==1)
                                //chunks = new int[count][];//Java 1.5 explodes with Exception in thread "main" java.lang.VerifyError:
                            	ch.chunks = new int[count][0];
                            break;
                        }
                    }
                }
            }
            //return chunks;
            return ch;
        }

        public static String[] toTokens(String src,String delim)
        {
            String[] tokens = null;
            int[][] chunks = toChunks(src,delim).chunks;
            if (chunks!=null && chunks.length>0)
            {
                tokens = new String[chunks.length];
                for (int i=0;i<chunks.length;i++)
                    tokens[i] = substring(src,chunks[i][0],chunks[i][1]);
            }
            return tokens;
        }

        public static int[] parseIntArray(String src,String delims)
        {
            int[] ints = null;
            int[][] chunks = toChunks(src,delims).chunks;
            if (chunks!=null && chunks.length>0)
            {
                ints = new int[chunks.length];
                for (int i=0;i<chunks.length;i++)
                    ints[i] = Integer.parseInt(substring(src,chunks[i][0],chunks[i][1]));
            }
            return ints;
        }

        public static String toString(Relation[] rels)
        {
            return toString(rels,"\n",";",",");
        }

        public static String toString(Relation[] rels,String relDelim,String fieldDelim,String itemDelim)
        {
        	StringBuilder sb = new StringBuilder();
            if (rels!=null)
                for (int i=0;i<rels.length;i++)
                    sb.append(toString(rels[i],fieldDelim,itemDelim)).append(relDelim);
            return sb.toString(); 
        }

        public static String toString(Item[] rels,String br)
        {
        	StringBuilder sb = new StringBuilder();
            if (rels!=null)
            {
                for (int i=0;i<rels.length;i++)
                {
                    sb
                    .append(rels[i].getId())
                    .append(' ')
                    .append(rels[i].getName())  
                    .append(br);
                }
            }
            return sb.toString(); 
        }

        public static String toString(RelevantItem[] rels,String br)
        {
        	StringBuilder sb = new StringBuilder();
            if (rels!=null)
            {
                for (int i=0;i<rels.length;i++)
                {
                    sb
                        .append(rels[i].getId()).append(' ')
                        // Web browsers other than IE can not use more than one value id in the list option
                        //.append(rels[i].getRelevantId()).append(' ')
                        .append(toString(rels[i].getConfirmation())).append(' ')
                        .append(fDoubleHigh.format(rels[i].getRelevance())).append(' ')		
                        .append(rels[i].getRelevantName()).append(br);
                }
            }
            return sb.toString(); 
        }

        public static String toString(Relation rel,String fieldDelim,String elementDelim)
        {
        	StringBuilder sb = new StringBuilder();
            //1<type>2<id>3<arity>4<objIds>5<posEvidence>6<negEvidence>
            //7<evidenceBase>8<weight>9<relianbility>10<approval>11<name>
            //<objIds> - is a sequence of Ids accrodingly to arity count
            sb
            	.append(rel.getType()).append(fieldDelim)
            	.append(rel.getId()).append(fieldDelim)
            	.append(rel.getArity()).append(fieldDelim)
                .append(toString(rel.getIds(),elementDelim)).append(fieldDelim)
                .append(toString(rel.getPosEvidence())).append(fieldDelim)
                .append(toString(rel.getNegEvidence())).append(fieldDelim)
                .append(toString(rel.getConfirmation())).append(fieldDelim);
            String name = rel.getName();
            if (name!=null)
                sb.append(name);
            return sb.toString();
        }

        public static String toString(int i)
        {
            return i==Types.NOTCONFIRMED? "?": Integer.toString(i); 
        }

        public static int toIntOrZero(String str,int radix){
        	return toIntOrDefault(str,radix,0);
        }
        
        public static int toIntOrDefault(String str,int radix,int defaultValue)
        {
        	try {
        		return Integer.parseInt(str, radix);
        	} catch (Exception e) {
        		return defaultValue;
        	}
        }
        
        public static String toHexString(int i,int len)
        {
            String s = Integer.toHexString(i);
            while (s.length() < len)
            	s = "0" + s;
            return s;
        }

        public static String toString(double d)
        {
            return d==0?"0":Double.toString(d); 
        }
        
        public static String toStringLow(double d)
        {
            return d==0?"0":fDoubleLow.format(d); 
        }
                
        public static Relation parseRelation(String line)
        {
            //1<type>2<id>3<arity>4<objIds>5<posEvidence>6<negEvidence>
            //7<evidenceBase>8<weight>9<relianbility>10<approval>11<name>
            //<objIds> - is a sequence of Ids accrodingly to arity count
            StoragerRelation rel = new StoragerRelation();
            int[][] chunk = StringUtil.toChunks(line,",;").chunks;
            rel.m_type = toInt(substring(line,chunk[0][0],chunk[0][1]));
            rel.m_id = toInt(substring(line,chunk[1][0],chunk[1][1]));
            int arity = toInt(substring(line,chunk[2][0],chunk[2][1]));
            if (arity>0)
            {
                rel.m_ids = new int[arity];
                for (int i=0;i<arity;i++)
                {
                    rel.m_ids[i] = toInt(substring(line,chunk[3+i][0],chunk[3+i][1]));
                }
            }
            rel.m_posEvidence = toDouble(substring(line,chunk[3+arity][0],chunk[3+arity][1]));
            rel.m_negEvidence = toDouble(substring(line,chunk[4+arity][0],chunk[4+arity][1]));
            rel.m_confirmation = StringUtil.toInt(substring(line,chunk[5+arity][0],chunk[5+arity][1]));
            rel.m_name = ((6+arity)<chunk.length && chunk[6+arity][0]<line.length())? line.substring(chunk[6+arity][0]): null;
            return rel;
        }

        public static int toInt(String s)
        {
            return s.equals("?") ? Types.NOTCONFIRMED: Integer.valueOf(fixDashSign(s)).intValue();
        }

        public static double toDouble(String s)
        {
            return Double.valueOf(fixDashSign(s)).doubleValue();
        }
        
        public static float toFloat(String s)
        {
            return Float.valueOf(fixDashSign(s)).floatValue();
        }
        
        //http://en.wikipedia.org/wiki/List_of_XML_and_HTML_character_entity_references
        //minus	−	U+2212 (8722)	HTML 4.0	HTMLsymbol	ISOtech	minus sign
        private static String fixDashSign(String s) {
        	return (s != null && s.length() > 1 && s.charAt(0) == MINUS) ? s.replace(MINUS, '-') : s;
        }
        
        public static boolean isDouble(String s)
        {
        	try {
        		toDouble(fixDashSign(s));
        		return true;
        	} catch (Exception e) {
        		return false;
        	}
        }
        
        public static boolean isInt(String s)
        {
        	try {
        		toInt(fixDashSign(s));
        		return true;
        	} catch (Exception e) {
        		return false;
        	}
        }
        
        public static float toFloat(String s,float def)//20070519
        {
        	try {
        		return toFloat(s);
        	} catch (Exception e) {
        		return def;
        	}
        }

        public static boolean isWord(String s)
        {            
            //Pattern pattern = Pattern.compile(TOKENREGEXP);
            //Matcher matcher = pattern.matcher(s);
            //boolean ok = matcher.find();
            boolean ok = s.matches(TOKENREGEXP);
        	return ok;
        }
        
        public static boolean toBoolean(String s,boolean def)//20070522
        {
        	try {
        		return Boolean.valueOf(s).booleanValue();
        	} catch (Exception e) {
        		return def;
        	}
        }

        public static String toString(int[] ids,String delim)
        {
        	StringBuilder sb = new StringBuilder();
            if (ids!=null && ids.length>0)
            {
                sb.append(toString(ids[0]));
                for (int i=1;i<ids.length;i++)
                    sb.append(delim).append(toString(ids[i]));
            }
            return sb.toString();
        }

        public static String toString(String[] strs,String br)
        {
        	StringBuilder sb = new StringBuilder();
            if (strs!=null && strs.length>0)
            {
                sb.append(strs[0]);
                for (int i=1;i<strs.length;i++)
                    sb.append(br).append(strs[i]);
            }
            return sb.toString();
        }

        public static String toStringFrom(String[] strs,int from)
        {
        	StringBuilder sb = new StringBuilder();
            if (strs!=null && strs.length>from)
            {
                sb.append(strs[from]);
                for (int i=from+1;i<strs.length;i++)
                    sb.append(" ").append(strs[i]);
            }
            return sb.toString();
        }
        public static String toHtml(String[] strs,int[] weightPercents)
        {
        	StringBuilder sb = new StringBuilder("<html><body>");
            if (strs!=null && strs.length>0)
            {
                for (int i=0;i<strs.length;i++)
                {
                    if (i>0)
                        sb.append(" ");
                    //int background=0xFFFFFF;
                    int foreground=0;
                    /* 
                    // version 1
                    {
                        //CCCCFF - light blue
                        //000040 - dark blue
                        int rg = 0xCC - (0xCC * weightPercents[i] / 255);
                        int bb = 0xFF - ((0xFF-0x40) * weightPercents[i] / 255);
                        background = (rg << 16) | (rg << 8) | bb;
                        foreground = weightPercents[i]>=50? 0xFFFFFF: 0;
                    }
                    */
                    //version 2
                    if (weightPercents[i]>=0)
                    {   
                        int c = 0xFF - (0xFF * weightPercents[i] / 100);
                        //background = (c << 16) | (c << 8) | c;
                        //String b = String.format("%02x%02x%02x",c,c,c).toUpperCase();
                        String b = toHexString(c,2);
                        foreground = (weightPercents[i]>=50)?0xFFFFFF:0;
                        sb.append("<font color=\"#").append(toHexString(foreground,6)).append("\">");
                        sb.append("<span style=\"background-color:#").append(b).append(b).append(b).append("\">");
                        sb.append(strs[i]);
                        sb.append("</span></font>");
                    }
                    else
                    {
                        sb.append(strs[i]);
                    }
                }
            }
            sb.append("</body></html>");
            return sb.toString();
        }
        
        public static String first(String s,int len) {
        	return s.length() > len ? s.substring(0,--len): s;
        }
}

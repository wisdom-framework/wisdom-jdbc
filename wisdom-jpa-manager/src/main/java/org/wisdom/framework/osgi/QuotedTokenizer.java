/*
 * #%L
 * Wisdom-Framework
 * %%
 * Copyright (C) 2013 - 2014 Wisdom Framework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wisdom.framework.osgi;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * A tokenizer used to parse manifest header.
 * Taken from BND code.
 */
public class QuotedTokenizer {
    String string;
    int index = 0;
    String separators;
    boolean returnTokens;
    String peek;
    char separator;

    /**
     * Creates a tokenizer.
     * @param string the string to parse
     * @param separators the separators
     * @param returnTokens whether or not tokens are returned
     */
    public QuotedTokenizer(String string, String separators, boolean returnTokens) {
        this.string = Preconditions.checkNotNull(string);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(separators));
        this.separators = separators;
        this.returnTokens = returnTokens;
    }

    /**
     * Creates a tokenizer.
     * @param string the string to parse
     * @param separators the separators
     */
    public QuotedTokenizer(String string, String separators) {
        this(string, separators, false);
    }

    /**
     * Retrieve next token.
     * @param separators the separators
     * @return the next token.
     */
    public String nextToken(String separators) {
        separator = 0;
        if (peek != null) {
            String tmp = peek;
            peek = null;
            return tmp;
        }

        if (index == string.length()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        boolean hadstring = false; // means no further trimming
        boolean validspace = false; // means include spaces

        while (index < string.length()) {
            char c = string.charAt(index++);

            if (Character.isWhitespace(c)) {
                if (index == string.length())
                    break;

                if (validspace)
                    sb.append(c);

                continue;
            }

            if (separators.indexOf(c) >= 0) {
                if (returnTokens)
                    peek = Character.toString(c);
                else
                    separator = c;
                break;
            }

            switch (c) {
                case '"':
                case '\'':
                    hadstring = true;
                    quotedString(sb, c);
                    // skip remaining space
                    validspace = false;
                    break;

                default:
                    sb.append(c);
                    validspace = true;
            }
        }
        String result = sb.toString();
        if (!hadstring)
            result = result.trim();

        if (result.length() == 0 && index == string.length())
            return null;
        return result;
    }

    /**
     * Retrieves next token.
     * @return the token
     */
    public String nextToken() {
        return nextToken(separators);
    }

    private void quotedString(StringBuilder sb, char c) {
        char quote = c;
        while (index < string.length()) {
            c = string.charAt(index++);
            if (c == quote)
                break;
            if (c == '\\' && index < string.length()) {
                char cc = string.charAt(index++);
                if (cc != quote)
                    sb.append("\\");
                c = cc;
            }
            sb.append(c);
        }
    }


    /**
     * Gets the current separator
     * @return the separator.
     */
    public char getSeparator() {
        return separator;
    }
}

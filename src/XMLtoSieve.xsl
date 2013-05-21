<?xml version="1.0" encoding="UTF-8"?>

<!--
Copyright (c) 2010 IETF Trust and the persons identified as authors of the code.  
All rights reserved. 

Redistribution and use in source and binary forms, with or without modification,
 are permitted provided that the following conditions are met:

·    Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer.
 
·    Redistributions in binary form must reproduce the above copyright notice, 
this list of conditions and the following disclaimer
 in the documentation and/or other materials provided with the distribution.
 
·    Neither the name of Internet Society, IETF or IETF Trust, nor the names of 
specific contributors, may be used to endorse or promote products derived from 
this software without specific prior written permission.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS “AS IS” AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE 
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

-->

<!-- Convert Sieve in XML to standard Sieve syntax -->

<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:sieve="urn:ietf:params:xml:ns:sieve">

    <xsl:output method="text" encoding="UTF-8"
                media-type="application/sieve"/>

    <!-- Only preserve whitespace in str elements -->
    <xsl:strip-space elements="*"/>
    <xsl:preserve-space elements="sieve:str"/>

    <!-- Match top level sieve node,
    start processing in sieve mode -->

    <xsl:template match="sieve:sieve">
        <xsl:apply-templates select="*" mode="sieve">
            <xsl:with-param name="prefix" select="''"/>
        </xsl:apply-templates>
    </xsl:template>

    <!-- Routine to properly literalize quotes in Sieve strings -->

    <xsl:template name="quote-string">
        <xsl:param name="str"/>
        <xsl:choose>
            <xsl:when test="not($str)"/>
            <xsl:when test="contains($str, '&quot;')">
                <xsl:call-template name="quote-string">
                    <xsl:with-param name="str"
                                    select="substring-before($str, '&quot;')"/>
                </xsl:call-template>
                <xsl:text>\&quot;</xsl:text>
                <xsl:call-template name="quote-string">
                    <xsl:with-param name="str"
                                    select="substring-after($str, '&quot;')"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:when test="contains($str, '\')">
                <xsl:call-template name="quote-string">
                    <xsl:with-param name="str"
                                    select="substring-before($str, '\')"/>
                </xsl:call-template>
                <xsl:text>\\</xsl:text>
                <xsl:call-template name="quote-string">
                    <xsl:with-param name="str"
                                    select="substring-after($str, '\')"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$str"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Sieve mode processing templates -->

    <xsl:template match="sieve:control|sieve:action" mode="sieve">
        <xsl:param name="prefix"/>
        <xsl:text xml:space="preserve">
   </xsl:text>
        <xsl:value-of select="$prefix"/>
        <xsl:value-of select="@name"/>
        <xsl:variable name="blockbegin"
                      select="generate-id(sieve:control|sieve:action)"/>
        <xsl:for-each select="*">
            <xsl:choose>
                <xsl:when test="self::sieve:str|self::sieve:num|
                           self::sieve:list|self::sieve:tag|
                           self::sieve:test">
                    <xsl:apply-templates select="." mode="sieve"/>
                </xsl:when>
                <xsl:when test="generate-id(.) = $blockbegin">
                    <xsl:text xml:space="preserve">
   </xsl:text>
                    <xsl:value-of select="$prefix"/>
                    <xsl:text>{</xsl:text>
                    <xsl:apply-templates select="." mode="sieve">
                        <xsl:with-param name="prefix"
                                        select="concat($prefix, '  ')"/>
                    </xsl:apply-templates>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:apply-templates select="." mode="sieve">
                        <xsl:with-param name="prefix" select="concat($prefix, '  ')"/>
                    </xsl:apply-templates>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:for-each>
        <xsl:choose>
            <xsl:when test="count(sieve:control|sieve:action) &gt; 0">
                <xsl:text xml:space="preserve">
   </xsl:text>
                <xsl:value-of select="$prefix"/>
                <xsl:text>}</xsl:text>
            </xsl:when>
            <xsl:otherwise>
                <xsl:text>;</xsl:text>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="sieve:preamble|sieve:postamble" mode="sieve">
        <xsl:param name="prefix"/>
        <xsl:apply-templates mode="sieve">
            <xsl:with-param name="prefix" select="$prefix"/>
        </xsl:apply-templates>
    </xsl:template>

    <xsl:template match="sieve:test" mode="sieve">
        <xsl:text xml:space="preserve"> </xsl:text>
        <xsl:value-of select="@name"/>
        <xsl:apply-templates select="*[not(self::sieve:test)]" mode="sieve"/>
        <xsl:if test="count(descendant::sieve:test) &gt; 0">
            <xsl:text> (</xsl:text>
            <xsl:for-each select="sieve:test">
                <xsl:apply-templates select="." mode="sieve"/>
                <xsl:if test="count(following-sibling::sieve:test) &gt; 0">
                    <xsl:text>,</xsl:text>
                </xsl:if>
            </xsl:for-each>
            <xsl:text> )</xsl:text>
        </xsl:if>
    </xsl:template>

    <xsl:template match="sieve:str" mode="sieve">
        <xsl:text> &quot;</xsl:text>
        <xsl:call-template name="quote-string">
            <xsl:with-param name="str" select="text()"/>
        </xsl:call-template>
        <xsl:text>&quot;</xsl:text>
    </xsl:template>

    <xsl:template match="sieve:num" mode="sieve">
        <xsl:text xml:space="preserve"> </xsl:text>
        <!-- Use numeric suffixes when possible -->
        <xsl:choose>
            <xsl:when test="(number(text()) mod 1073741824) = 0">
                <xsl:value-of select="number(text()) div 1073741824"/>
                <xsl:text>G</xsl:text>
            </xsl:when>
            <xsl:when test="(number(text()) mod 1048576) = 0">
                <xsl:value-of select="number(text()) div 1048576"/>
                <xsl:text>M</xsl:text>
            </xsl:when>
            <xsl:when test="(number(text()) mod 1024) = 0">
                <xsl:value-of select="number(text()) div 1024"/>
                <xsl:text>K</xsl:text>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="text()"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="sieve:list" mode="sieve">
        <xsl:text> [</xsl:text>
        <xsl:for-each select="sieve:str">
            <xsl:apply-templates select="." mode="sieve"/>
            <xsl:if test="count(following-sibling::sieve:str) &gt; 0">
                <xsl:text>,</xsl:text>
            </xsl:if>
        </xsl:for-each>
        <xsl:text> ]</xsl:text>
    </xsl:template>

    <xsl:template match="sieve:tag" mode="sieve">
        <xsl:text> :</xsl:text>
        <xsl:value-of select="text()"/>
    </xsl:template>

    <xsl:template match="sieve:comment" mode="sieve">
        <xsl:param name="prefix"/>
        <xsl:text xml:space="preserve">
   </xsl:text>
        <xsl:value-of select="$prefix"/>
        <xsl:text>/*</xsl:text>
        <xsl:value-of select="."/>
        <xsl:value-of select="$prefix"/>
        <xsl:text>*/</xsl:text>
    </xsl:template>

    <!-- Convert display information into structured comments -->

    <xsl:template match="sieve:displayblock" mode="sieve">
        <xsl:param name="prefix"/>
        <xsl:text xml:space="preserve">
   </xsl:text>
        <xsl:value-of select="$prefix"/>
        <xsl:text>/* [*</xsl:text>
        <xsl:apply-templates select="@*" mode="copy"/>
        <xsl:text> */</xsl:text>
        <xsl:apply-templates select="*" mode="sieve">
            <xsl:with-param name="prefix" select="$prefix"/>
        </xsl:apply-templates>
        <xsl:text xml:space="preserve">
   </xsl:text>
        <xsl:value-of select="$prefix"/>
        <xsl:text>/* *] */</xsl:text>
    </xsl:template>

    <xsl:template match="sieve:displaydata" mode="sieve">
        <xsl:param name="prefix"/>
        <xsl:text xml:space="preserve">
   </xsl:text>
        <xsl:value-of select="$prefix"/>
        <xsl:text>/* [|</xsl:text>
        <xsl:apply-templates mode="copy">
            <xsl:with-param name="prefix"
                            select="concat($prefix, '  ')"/>
        </xsl:apply-templates>
        <xsl:text xml:space="preserve">
   </xsl:text>
        <xsl:value-of select="$prefix"/>
        <xsl:text>   |] */</xsl:text>
    </xsl:template>

    <!-- Copy unrecnognized nodes and their descendants -->

    <xsl:template match="*" mode="sieve">
        <xsl:param name="prefix"/>
        <xsl:text xml:space="preserve">
   </xsl:text>
        <xsl:value-of select="$prefix"/>
        <xsl:text>/* [/</xsl:text>
        <xsl:apply-templates select="." mode="copy">
            <xsl:with-param name="prefix" select="concat($prefix, '  ')"/>
        </xsl:apply-templates>
        <xsl:text xml:space="preserve">
   </xsl:text>
        <xsl:value-of select="$prefix"/>
        <xsl:text>   /] */</xsl:text>
    </xsl:template>

    <!-- Copy mode processing templates -->

    <xsl:template match="*[not(node())]" mode="copy">
        <xsl:param name="prefix"/>
        <xsl:text xml:space="preserve">
   </xsl:text>
        <xsl:value-of select="$prefix"/>
        <xsl:text>&lt;</xsl:text>
        <xsl:value-of select="name()"/>
        <xsl:apply-templates select="@*" mode="copy"/>
        <xsl:text>/&gt;</xsl:text>
    </xsl:template>

    <xsl:template match="*[node()]" mode="copy">
        <xsl:param name="prefix"/>
        <xsl:text xml:space="preserve">
   </xsl:text>
        <xsl:value-of select="$prefix"/>
        <xsl:text>&lt;</xsl:text>
        <xsl:value-of select="name()"/>
        <xsl:apply-templates select="@*" mode="copy"/>
        <xsl:text>&gt;</xsl:text>
        <xsl:apply-templates mode="copy">
            <xsl:with-param name="prefix"
                            select="concat($prefix, '  ')"/>
        </xsl:apply-templates>
        <xsl:if test="*[last()][not(text())]">
            <xsl:text xml:space="preserve">
   </xsl:text>
            <xsl:value-of select="$prefix"/>
        </xsl:if>
        <xsl:text>&lt;/</xsl:text>
        <xsl:value-of select="name()"/>
        <xsl:text>&gt;</xsl:text>
    </xsl:template>

    <xsl:template match="@*" mode="copy">
        <xsl:text> </xsl:text>
        <xsl:value-of select="name()"/>
        <xsl:text>="</xsl:text>
        <xsl:value-of select="."/>
        <xsl:text>"</xsl:text>
    </xsl:template>

</xsl:stylesheet>
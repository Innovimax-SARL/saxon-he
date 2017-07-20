<xsl:stylesheet 
      xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='3.0'
      xmlns:bar="http://apache.org/bar"
      xmlns:saxon="http://saxon.sf.net/"
      xmlns:php="http://php.net/xsl"
      xmlns:xs="http://www.w3.org/2001/XMLSchema"
      exclude-result-prefixes="bar"
      >
      
  <xsl:include href="../../xsl/inc1/inc1.xsl"/>
      
  <xsl:param name="a-param">default param value</xsl:param>
  
  <xsl:param name="testdoc">
    <testdoc>testdoc-data</testdoc>
  </xsl:param>

  <xsl:output encoding="iso-8859-1"/>

  <xsl:template match="/">
    ﻿<xsl:variable name="phpCall" select="php:function('userFunction', 'test', .)" />
    <extension-function-test>Call to saxon:php-call:
      <xsl:if test="not(empty($phpCall))">
        Result from phpCall:
        ﻿<xsl:value-of select="$phpCall" /> 
      </xsl:if>
      <xsl:if test="empty($phpCall)">
        Empty result FOUND-----:
        ﻿<xsl:value-of select="$phpCall" /> 
      </xsl:if>
    </extension-function-test>
    <!--<test-extension-func><xsl:value-of select="php:function('myfunc', 2)"/></test-extension-func>-->
    <xsl:comment><xsl:value-of select="system-property('xsl:product-version')"/></xsl:comment>
    <xsl:next-match/>
  </xsl:template>  
  
  <xsl:template match="bar:element">
    <bar>
      <param-val>
        <xsl:value-of select="$a-param"/><xsl:text>, </xsl:text>
        <xsl:value-of select="$my-var"/>
      </param-val>
      <data><xsl:apply-templates/></data>
    </bar>
  </xsl:template>
      
  <xsl:template 
      match="@*|*|text()|processing-instruction()">
    <xsl:copy>
      <xsl:apply-templates 
         select="@*|*|text()|processing-instruction()"/>
    </xsl:copy>
  </xsl:template>
</xsl:stylesheet>

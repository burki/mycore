<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xalan="http://xml.apache.org/xalan"
  xmlns:i18n="xalan://org.mycore.services.i18n.MCRTranslation" xmlns:acl="xalan://org.mycore.access.MCRAccessManager" xmlns:mcr="http://www.mycore.org/"
  xmlns:xlink="http://www.w3.org/1999/xlink" exclude-result-prefixes="xlink mcr i18n acl" version="1.0">
  <xsl:param select="'local'" name="objectHost" />
  <!--Template for result list hit: see results.xsl -->
  <xsl:template match="mcr:hit[contains(@id,'_mods_')]">
    <xsl:param name="mcrobj" />
    <xsl:param name="mcrobjlink" />
    <xsl:variable select="100" name="DESCRIPTION_LENGTH" />
    <xsl:variable select="@host" name="host" />
    <xsl:variable name="obj_id">
      <xsl:value-of select="@id" />
    </xsl:variable>
    <tr>
      <td colspan="2" class="resultTitle">
        <xsl:call-template name="objectLink">
          <xsl:with-param select="$mcrobj" name="mcrobj" />
        </xsl:call-template>
      </td>
    </tr>
    <tr>
      <td colspan="2" class="description">
        <div>please edit &lt;template match=mcr:hit[contains(@id,'_mods_')]&gt; for object type: mods</div>
        <!-- you could insert here a preview for your metadata, e.g. uncomment the next block and replace "your-tags/here" by something of your 
          metadata -->
        <!-- <div> short description: <xsl:call-template name="printI18N"> <xsl:with-param name="nodes" select="$mcrobj/metadata/your-tags/here" 
          /> </xsl:call-template> </div> -->
        <span class="properties">
          <xsl:variable name="date">
            <xsl:call-template name="formatISODate">
              <xsl:with-param select="$mcrobj/service/servdates/servdate[@type='modifydate']" name="date" />
              <xsl:with-param select="i18n:translate('metaData.date')" name="format" />
            </xsl:call-template>
          </xsl:variable>
          <xsl:value-of select="i18n:translate('results.lastChanged',$date)" />
        </span>
      </td>
    </tr>
  </xsl:template>
  <!--Template for generated link names and result titles: see mycoreobject.xsl, results.xsl, MyCoReLayout.xsl -->
  <xsl:template priority="1" mode="resulttitle" match="/mycoreobject[contains(@ID,'_mods_')]">
    <xsl:choose>
      <!-- you could insert any title-like metadata here, e.g. replace "your-tags/here" by something of your metadata -->
      <xsl:when test="./metadata/your-tags">
        <xsl:call-template name="printI18N">
          <xsl:with-param select="./metadata/your-tags/here" name="nodes" />
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="@label" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!--Template for title in metadata view: see mycoreobject.xsl -->
  <xsl:template priority="1" mode="title" match="/mycoreobject[contains(@ID,'_mods_')]">
    <xsl:choose>
      <!-- you could insert any title-like metadata here, e.g. replace "your-tags/here" by something of your metadata -->
      <xsl:when test="./metadata/your-tags">
        <xsl:call-template name="printI18N">
          <xsl:with-param select="./metadata/your-tags/here" name="nodes" />
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="@ID" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!--Template for metadata view: see mycoreobject.xsl -->
  <xsl:template priority="1" mode="present" match="/mycoreobject[contains(@ID,'_mods_')]">
    <xsl:variable name="objectBaseURL">
      <xsl:if test="$objectHost != 'local'">
        <xsl:value-of select="document('webapp:hosts.xml')/mcr:hosts/mcr:host[@alias=$objectHost]/mcr:url[@type='object']/@href" />
      </xsl:if>
      <xsl:if test="$objectHost = 'local'">
        <xsl:value-of select="concat($WebApplicationBaseURL,'receive/')" />
      </xsl:if>
    </xsl:variable>
    <xsl:variable name="staticURL">
      <xsl:value-of select="concat($objectBaseURL,@ID)" />
    </xsl:variable>
    <table cellspacing="0" cellpadding="0" id="metaData">
      <!--1***modsContainer************************************* -->
      <xsl:call-template name="printMetaDate">
        <xsl:with-param select="./metadata/def.modsContainer/modsContainer" name="nodes" />
        <xsl:with-param select="i18n:translate('metaData.mods.modsContainer')" name="label" />
      </xsl:call-template>
      <!--*** Editor Buttons ************************************* -->
      <xsl:call-template name="editobject_with_der">
        <xsl:with-param select="./@ID" name="id" />
      </xsl:call-template>
      <xsl:variable name="typeToken">
        <types>
          <xsl:call-template name="Tokenizer">
            <xsl:with-param select="'mods'" name="string" />
          </xsl:call-template>
        </types>
      </xsl:variable>
      <xsl:apply-templates mode="addChild" select="xalan:nodeset($typeToken)/types">
        <xsl:with-param name="id" select="./@ID" />
      </xsl:apply-templates>
      <!--*** List children per object type ************************************* -->
      <!-- 1.) get a list of objectTypes of all child elements 2.) remove duplicates from this list 3.) for-each objectTyp id list child elements -->
      <xsl:variable name="objectTypes">
        <xsl:for-each select="./structure/children/child/@xlink:href">
          <id>
            <xsl:copy-of select="substring-before(substring-after(.,'_'),'_')" />
          </id>
        </xsl:for-each>
      </xsl:variable>
      <xsl:variable select="xalan:nodeset($objectTypes)/id[not(.=following::id)]" name="unique-ids" />
      <!-- the for-each would iterate over <id> with root not beeing /mycoreobject so we save the current node in variable context to access 
        needed nodes -->
      <xsl:variable select="." name="context" />
      <xsl:for-each select="$unique-ids">
        <xsl:variable select="." name="thisObjectType" />
        <xsl:variable name="label">
          <xsl:choose>
            <xsl:when test="count($context/structure/children/child[contains(@xlink:href,$thisObjectType)])=1">
              <xsl:value-of select="i18n:translate(concat('metaData.',$thisObjectType,'.[singular]'))" />
            </xsl:when>
            <xsl:otherwise>
              <xsl:value-of select="i18n:translate(concat('metaData.',$thisObjectType,'.[plural]'))" />
            </xsl:otherwise>
          </xsl:choose>
        </xsl:variable>
        <xsl:call-template name="printMetaDate">
          <xsl:with-param select="$context/structure/children/child[contains(@xlink:href, concat('_',$thisObjectType,'_'))]"
            name="nodes" />
          <xsl:with-param select="$label" name="label" />
        </xsl:call-template>
      </xsl:for-each>
      <xsl:apply-templates mode="printDerivates" select=".">
        <xsl:with-param select="$staticURL" name="staticURL" />
      </xsl:apply-templates>
      <!--*** Created ************************************* -->
      <xsl:call-template name="printMetaDate">
        <xsl:with-param select="./service/servdates/servdate[@type='createdate']" name="nodes" />
        <xsl:with-param select="i18n:translate('metaData.createdAt')" name="label" />
      </xsl:call-template>
      <!--*** Last Modified ************************************* -->
      <xsl:call-template name="printMetaDate">
        <xsl:with-param select="./service/servdates/servdate[@type='modifydate']" name="nodes" />
        <xsl:with-param select="i18n:translate('metaData.lastChanged')" name="label" />
      </xsl:call-template>
      <!--*** MyCoRe-ID ************************************* -->
      <tr>
        <td class="metaname">
          <xsl:value-of select="concat(i18n:translate('metaData.ID'),' :')" />
        </td>
        <td class="metavalue">
          <xsl:value-of select="./@ID" />
        </td>
      </tr>
    </table>
  </xsl:template>
  <xsl:template mode="addChild" match="types[token]">
    <xsl:param name="id" />
    <xsl:param name="layout" />
    <xsl:param select="concat('&amp;_xml_structure%2Fparents%2Fparent%2F%40href=',$id)" name="xmltempl" />
    <xsl:variable name="suffix">
      <xsl:if test="string-length($layout)&gt;0">
        <xsl:value-of select="concat('&amp;layout=',$layout)" />
      </xsl:if>
    </xsl:variable>
    <xsl:if test="acl:checkPermission($id,'writedb')">
      <tr>
        <td class="metaname">
          <xsl:value-of select="concat(i18n:translate('metaData.addChildObject'),':')" />
        </td>
        <td class="metavalue">
          <ul>
            <xsl:for-each select="token">
              <xsl:variable select="." name="type" />
              <li>
                <a href="{$ServletsBaseURL}MCRStartEditorServlet{$HttpSession}?type={$type}&amp;step=author&amp;todo=wnewobj{$suffix}{$xmltempl}">
                  <xsl:value-of select="i18n:translate(concat('metaData.',$type,'.[singular]'))" />
                </a>
              </li>
            </xsl:for-each>
          </ul>
        </td>
      </tr>
    </xsl:if>
  </xsl:template>
  <xsl:template mode="printDerivates" match="/mycoreobject">
    <xsl:param name="staticURL" />
    <xsl:param name="layout" />
    <xsl:param name="xmltempl" />
    <xsl:variable select="substring-before(substring-after(./@ID,'_'),'_')" name="type" />
    <xsl:variable name="suffix">
      <xsl:if test="string-length($layout)&gt;0">
        <xsl:value-of select="concat('&amp;layout=',$layout)" />
      </xsl:if>
    </xsl:variable>
    <xsl:if test="./structure/derobjects">
      <tr>
        <td style="vertical-align:top;" class="metaname">
          <xsl:value-of select="i18n:translate('metaData.mods.[derivates]')" />
        </td>
        <td class="metavalue">
          <xsl:if test="$objectHost != 'local'">
            <a href="{$staticURL}">nur auf original Server</a>
          </xsl:if>
          <xsl:if test="$objectHost = 'local'">
            <xsl:for-each select="./structure/derobjects/derobject">
              <table cellpadding="0" cellspacing="0" border="0" width="100%">
                <tr>
                  <td valign="top" align="left">
                    <xsl:variable select="@xlink:href" name="deriv" />
                    <div class="derivateBox">
                      <xsl:variable select="concat('mcrobject:',$deriv)" name="derivlink" />
                      <xsl:variable select="document($derivlink)" name="derivate" />
                      <xsl:apply-templates select="$derivate/mycorederivate/derivate/internals" />
                      <xsl:apply-templates select="$derivate/mycorederivate/derivate/externals" />
                    </div>
                    <!-- MCR-IView ..start -->
                    <xsl:call-template name="derivateView">
                      <xsl:with-param name="derivateID" select="$deriv" />
                    </xsl:call-template>
                    <!-- MCR - IView ..end -->
                  </td>
                  <xsl:if test="acl:checkPermission(./@ID,'writedb')">
                    <td align="right" valign="top">
                      <a
                        href="{$ServletsBaseURL}MCRStartEditorServlet{$HttpSession}?type={$type}&amp;re_mcrid={../../../@ID}&amp;se_mcrid={@xlink:href}&amp;te_mcrid={@xlink:href}&amp;todo=saddfile{$suffix}{$xmltempl}">
                        <img title="Datei hinzufügen" src="{$WebApplicationBaseURL}images/workflow_deradd.gif" />
                      </a>
                      <a
                        href="{$ServletsBaseURL}MCRStartEditorServlet{$HttpSession}?type={$type}&amp;re_mcrid={../../../@ID}&amp;se_mcrid={@xlink:href}&amp;te_mcrid={@xlink:href}&amp;todo=seditder{$suffix}{$xmltempl}">
                        <img title="Derivat bearbeiten" src="{$WebApplicationBaseURL}images/workflow_deredit.gif" />
                      </a>
                      <a
                        href="{$ServletsBaseURL}MCRStartEditorServlet{$HttpSession}?type={$type}&amp;re_mcrid={../../../@ID}&amp;se_mcrid={@xlink:href}&amp;te_mcrid={@xlink:href}&amp;todo=sdelder{$suffix}{$xmltempl}">
                        <img title="Derivat löschen" src="{$WebApplicationBaseURL}images/workflow_derdelete.gif" />
                      </a>
                    </td>
                  </xsl:if>
                </tr>
              </table>
            </xsl:for-each>
          </xsl:if>
        </td>
      </tr>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>

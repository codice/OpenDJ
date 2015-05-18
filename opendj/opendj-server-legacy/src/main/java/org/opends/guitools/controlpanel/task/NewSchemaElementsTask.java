/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.task;

import static org.forgerock.util.Utils.*;
import static org.opends.messages.AdminToolMessages.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.CommonSchemaElements;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.SchemaFileElement;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

import static org.opends.server.types.CommonSchemaElements.*;

/**
 * An abstract class used to re-factor some code between the different tasks
 * that create elements in the schema.
 *
 */
public class NewSchemaElementsTask extends Task
{
  LinkedHashSet<ObjectClass> ocsToAdd = new LinkedHashSet<>();
  LinkedHashSet<AttributeType> attrsToAdd = new LinkedHashSet<>();

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param ocsToAdd the object classes that must be created in order.
   * @param attrsToAdd the attributes that must be created in order.
   */
  public NewSchemaElementsTask(ControlPanelInfo info, ProgressDialog dlg,
      LinkedHashSet<ObjectClass> ocsToAdd,
      LinkedHashSet<AttributeType> attrsToAdd)
  {
    super(info, dlg);
    this.ocsToAdd.addAll(ocsToAdd);
    this.attrsToAdd.addAll(attrsToAdd);
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getBackends()
  {
    return Collections.emptySet();
  }

  /** {@inheritDoc} */
  @Override
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<LocalizableMessage> incompatibilityReasons)
  {
    boolean canLaunch = true;
    if (state == State.RUNNING &&
        (taskToBeLaunched.getType() == Task.Type.DELETE_SCHEMA_ELEMENT ||
         taskToBeLaunched.getType() == Task.Type.MODIFY_SCHEMA_ELEMENT ||
         taskToBeLaunched.getType() == Task.Type.NEW_SCHEMA_ELEMENT))
    {
      incompatibilityReasons.add(getIncompatibilityMessage(this,
            taskToBeLaunched));
      canLaunch = false;
    }
    return canLaunch;
  }

  /** {@inheritDoc} */
  @Override
  public void runTask()
  {
    state = State.RUNNING;
    lastException = null;

    try
    {
      updateSchema();
      state = State.FINISHED_SUCCESSFULLY;
    }
    catch (Throwable t)
    {
      lastException = t;
      state = State.FINISHED_WITH_ERROR;
    }
  }

  /** {@inheritDoc} */
  @Override
  public Type getType()
  {
    return Type.NEW_SCHEMA_ELEMENT;
  }

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getTaskDescription()
  {
    if (attrsToAdd.size() == 1 && ocsToAdd.isEmpty())
    {
      String attributeName = attrsToAdd.iterator().next().getNameOrOID();
      return INFO_CTRL_PANEL_NEW_ATTRIBUTE_TASK_DESCRIPTION.get(attributeName);
    }
    else if (ocsToAdd.size() == 1 && attrsToAdd.isEmpty())
    {
      String ocName = ocsToAdd.iterator().next().getNameOrOID();
      return INFO_CTRL_PANEL_NEW_OBJECTCLASS_TASK_DESCRIPTION.get(ocName);
    }
    else
    {
      ArrayList<String> attrNames = new ArrayList<>();
      for (AttributeType attribute : attrsToAdd)
      {
        attrNames.add(attribute.getNameOrOID());
      }
      ArrayList<String> ocNames = new ArrayList<>();
      for (ObjectClass oc : ocsToAdd)
      {
        ocNames.add(oc.getNameOrOID());
      }
      if (ocNames.isEmpty())
      {
        return INFO_CTRL_PANEL_NEW_ATTRIBUTES_TASK_DESCRIPTION.get(joinAsString(", ", attrNames));
      }
      else if (attrNames.isEmpty())
      {
        return INFO_CTRL_PANEL_NEW_OBJECTCLASSES_TASK_DESCRIPTION.get(joinAsString(", ", ocNames));
      }
      else
      {
        return INFO_CTRL_PANEL_NEW_SCHEMA_ELEMENTS_TASK_DESCRIPTION.get(
            joinAsString(", ", attrNames),
            joinAsString(", ", ocNames));
      }
    }
  }

  /**
   * Update the schema.
   * @throws OpenDsException if an error occurs.
   */
  private void updateSchema() throws OpenDsException
  {
    if (isServerRunning())
    {
      updateSchemaOnline();
    }
    else
    {
      updateSchemaOffline();
    }
  }

  /** {@inheritDoc} */
  @Override
  protected String getCommandLinePath()
  {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  protected List<String> getCommandLineArguments()
  {
    return Collections.emptyList();
  }

  private void updateSchemaOnline() throws OpenDsException
  {
    // Add the schema elements one by one: we are not sure that the server
    // will handle the adds sequentially if we only send one modification.
    for (AttributeType attr : attrsToAdd)
    {
      addAttributeOnline(attr);
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          getProgressDialog().appendProgressHtml(Utilities.applyFont("<br><br>",
              ColorAndFontConstants.progressFont));
        }
      });
    }
    for (ObjectClass oc : ocsToAdd)
    {
      addObjectClassOnline(oc);
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          getProgressDialog().appendProgressHtml(Utilities.applyFont("<br><br>",
              ColorAndFontConstants.progressFont));
        }
      });
    }
  }

  private void updateSchemaOffline() throws OpenDsException
  {
    // Group the changes in the same schema file.
    LinkedHashMap<String, List<AttributeType>> hmAttrs = copy(attrsToAdd);
    LinkedHashMap<String, List<ObjectClass>> hmOcs = copy(ocsToAdd);

    LinkedHashSet<String> allFileNames = new LinkedHashSet<>();
    allFileNames.addAll(hmAttrs.keySet());
    allFileNames.addAll(hmOcs.keySet());
    for (String fileName : allFileNames)
    {
      List<AttributeType> attrs = get(hmAttrs, fileName);
      List<ObjectClass> ocs = get(hmOcs, fileName);

      if (fileName.equals(""))
      {
        fileName = null;
      }
      updateSchemaOffline(fileName, attrs, ocs);
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          getProgressDialog().appendProgressHtml(Utilities.applyFont("<br><br>",
              ColorAndFontConstants.progressFont));
        }
      });
    }
  }

  private <T extends SchemaFileElement> List<T> get(
      LinkedHashMap<String, List<T>> hmElems, String fileName)
  {
    List<T> elems = hmElems.get(fileName);
    if (elems != null)
    {
      return elems;
    }
    return Collections.emptyList();
  }

  private <T extends SchemaFileElement> LinkedHashMap<String, List<T>> copy(
      LinkedHashSet<T> elemsToAdd)
  {
    LinkedHashMap<String, List<T>> hmElems = new LinkedHashMap<>();
    for (T elem : elemsToAdd)
    {
      String fileName = CommonSchemaElements.getSchemaFile(elem);
      if (fileName == null)
      {
        fileName = "";
      }
      List<T> elems = hmElems.get(fileName);
      if (elems == null)
      {
        elems = new ArrayList<>();
        hmElems.put(fileName, elems);
      }
      elems.add(elem);
    }
    return hmElems;
  }

  private void addAttributeOnline(final AttributeType attribute)
  throws OpenDsException
  {
    SwingUtilities.invokeLater(new Runnable()
    {
      /** {@inheritDoc} */
      @Override
      public void run()
      {
        printEquivalentCommandLineToAddOnline(attribute);
        getProgressDialog().appendProgressHtml(
            Utilities.getProgressWithPoints(
                INFO_CTRL_PANEL_CREATING_ATTRIBUTE_PROGRESS.get(
                    attribute.getNameOrOID()),
                    ColorAndFontConstants.progressFont));
      }
    });
    try
    {
      BasicAttribute attr = new BasicAttribute(getAttributeName(attribute));
      attr.add(getValueOnline(attribute));
      ModificationItem mod = new ModificationItem(DirContext.ADD_ATTRIBUTE,
          attr);
      getInfo().getDirContext().modifyAttributes(
          ConfigConstants.DN_DEFAULT_SCHEMA_ROOT,
          new ModificationItem[]  { mod });
    }
    catch (NamingException ne)
    {
      throw new OnlineUpdateException(
          ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(ne), ne);
    }
    notifyConfigurationElementCreated(attribute);
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        getProgressDialog().appendProgressHtml(
            Utilities.getProgressDone(ColorAndFontConstants.progressFont));
      }
    });
  }

  private void addObjectClassOnline(final ObjectClass objectClass)
  throws OpenDsException
  {
    SwingUtilities.invokeLater(new Runnable()
    {
      /** {@inheritDoc} */
      @Override
      public void run()
      {
        printEquivalentCommandLineToAddOnline(objectClass);
        getProgressDialog().appendProgressHtml(
            Utilities.getProgressWithPoints(
                INFO_CTRL_PANEL_CREATING_OBJECTCLASS_PROGRESS.get(
                    objectClass.getNameOrOID()),
                    ColorAndFontConstants.progressFont));
      }
    });
    try
    {
      BasicAttribute attr = new BasicAttribute(getAttributeName(objectClass));
      attr.add(getValueOnline(objectClass));
      ModificationItem mod = new ModificationItem(DirContext.ADD_ATTRIBUTE,
          attr);
      getInfo().getDirContext().modifyAttributes(
          ConfigConstants.DN_DEFAULT_SCHEMA_ROOT,
          new ModificationItem[]  { mod });
    }
    catch (NamingException ne)
    {
      throw new OnlineUpdateException(
          ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(ne), ne);
    }
    notifyConfigurationElementCreated(objectClass);
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        getProgressDialog().appendProgressHtml(
            Utilities.getProgressDone(ColorAndFontConstants.progressFont));
      }
    });
  }

  private String getValueOnline(CommonSchemaElements element)
  {
    return element.toString();
  }

  private String getValueOffline(CommonSchemaElements element)
  {
    final Map<String, List<String>> props = element.getExtraProperties();
    List<String> previousValues =
        props.get(ServerConstants.SCHEMA_PROPERTY_FILENAME);
    setExtraProperty(element, ServerConstants.SCHEMA_PROPERTY_FILENAME, null);
    String attributeWithoutFileDefinition = element.toString();

    if (previousValues != null && !previousValues.isEmpty())
    {
      ArrayList<String> vs = new ArrayList<>(previousValues);
      element.setExtraProperty(ServerConstants.SCHEMA_PROPERTY_FILENAME, vs);
    }
    return attributeWithoutFileDefinition;
  }

  private void printEquivalentCommandLineToAddOnline(
      CommonSchemaElements element)
  {
    ArrayList<String> args = new ArrayList<>();
    args.add("-a");
    args.addAll(getObfuscatedCommandLineArguments(
        getConnectionCommandLineArguments(true, true)));
    args.add(getNoPropertiesFileArgument());

    String equiv = getEquivalentCommandLine(getCommandLinePath("ldapmodify"),
        args);

    StringBuilder sb = new StringBuilder();
    LocalizableMessage msg;
    if (element instanceof AttributeType)
    {
      msg = INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_ADD_ATTRIBUTE_ONLINE.get(
          element.getNameOrOID());
    }
    else
    {
      msg = INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_ADD_OBJECTCLASS_ONLINE.get(
          element.getNameOrOID());
    }
    sb.append(msg).append("<br><b>");
    sb.append(equiv);
    sb.append("<br>");
    sb.append("dn: cn=schema<br>");
    sb.append("changetype: modify<br>");
    sb.append("add: ").append(getAttributeName(element)).append("<br>");
    sb.append(getAttributeName(element)).append(": ")
        .append(getValueOnline(element));
    sb.append("</b><br><br>");
    getProgressDialog().appendProgressHtml(Utilities.applyFont(sb.toString(),
        ColorAndFontConstants.progressFont));
  }

  private String getAttributeName(CommonSchemaElements element)
  {
    if (element instanceof AttributeType)
    {
      return ConfigConstants.ATTR_ATTRIBUTE_TYPES;
    }
    else
    {
      return ConfigConstants.ATTR_OBJECTCLASSES;
    }
  }

  private void updateSchemaOffline(String file,
      final List<AttributeType> attributes,
      final List<ObjectClass> objectClasses) throws OpenDsException
  {
    if (file == null)
    {
      file = ConfigConstants.FILE_USER_SCHEMA_ELEMENTS;
    }
    File f = new File(file);
    if (!f.isAbsolute())
    {
      f = new File(
        DirectoryServer.getEnvironmentConfig().getSchemaDirectory(),
        file);
    }
    final String fileName = f.getAbsolutePath();
    final boolean isSchemaFileDefined = isSchemaFileDefined(fileName);
    SwingUtilities.invokeLater(new Runnable()
    {
      /** {@inheritDoc} */
      @Override
      public void run()
      {
        final ProgressDialog progressDialog = getProgressDialog();
        final String command = equivalentCommandToAddOffline(
            fileName, isSchemaFileDefined, attributes, objectClasses);
        progressDialog.appendProgressHtml(
            Utilities.applyFont(command,
                ColorAndFontConstants.progressFont));

        if (attributes.size() == 1 && objectClasses.isEmpty())
        {
          String attributeName = attributes.get(0).getNameOrOID();
          progressDialog.appendProgressHtml(
              Utilities.getProgressWithPoints(
                  INFO_CTRL_PANEL_CREATING_ATTRIBUTE_PROGRESS.get(attributeName),
                      ColorAndFontConstants.progressFont));
        }
        else if (objectClasses.size() == 1 && attributes.isEmpty())
        {
          String ocName = objectClasses.get(0).getNameOrOID();
          progressDialog.appendProgressHtml(
              Utilities.getProgressWithPoints(
                  INFO_CTRL_PANEL_CREATING_OBJECTCLASS_PROGRESS.get(ocName),
                      ColorAndFontConstants.progressFont));
        }
        else
        {
          progressDialog.appendProgressHtml(
              Utilities.getProgressWithPoints(
                  INFO_CTRL_PANEL_UPDATING_SCHEMA_FILE_PROGRESS.get(fileName),
                      ColorAndFontConstants.progressFont));
        }
      }
    });

    updateSchemaFile(fileName, isSchemaFileDefined, attributes, objectClasses);

    for (AttributeType attr : attributes)
    {
      notifyConfigurationElementCreated(attr);
    }
    for (ObjectClass oc : objectClasses)
    {
      notifyConfigurationElementCreated(oc);
    }
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        getProgressDialog().appendProgressHtml(
            Utilities.getProgressDone(ColorAndFontConstants.progressFont));
      }
    });
  }

  private String equivalentCommandToAddOffline(String schemaFile,
      boolean isSchemaFileDefined,
      List<AttributeType> attributes,
      List<ObjectClass> objectClasses)
  {
    ArrayList<String> names = new ArrayList<>();
    for (AttributeType attr : attributes)
    {
      names.add(attr.getNameOrOID());
    }
    for (ObjectClass oc : objectClasses)
    {
      names.add(oc.getNameOrOID());
    }
    final String namesString = joinAsString(", ", names);

    final StringBuilder sb = new StringBuilder();
    if (isSchemaFileDefined)
    {
      sb.append(INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_ADD_SCHEMA_ELEMENT_OFFLINE.get(namesString, schemaFile))
        .append("<br><b>");
    }
    else
    {
      sb.append(INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_ADD_SCHEMA_ENTRY_OFFLINE.get(namesString, schemaFile))
        .append("<br><b>");
      for (String line : getSchemaEntryLines())
      {
        sb.append(line);
        sb.append("<br>");
      }
    }

    for (AttributeType attribute : attributes)
    {
      sb.append(getAttributeName(attribute)).append(": ")
          .append(getValueOffline(attribute)).append("<br>");
    }
    for (ObjectClass oc : objectClasses)
    {
      sb.append(getAttributeName(oc)).append(": ")
          .append(getValueOffline(oc)).append("<br>");
    }
    sb.append("</b><br><br>");
    return sb.toString();
  }

  /**
   * Returns whether the file defined in the schema element exists or not.
   * @param schemaFile the path to the schema file.
   * @return <CODE>true</CODE> if the schema file is defined and
   * <CODE>false</CODE> otherwise.
   */
  private boolean isSchemaFileDefined(String schemaFile)
  {
    boolean schemaDefined = false;
    LDIFReader reader = null;
    try
    {
      reader = new LDIFReader(new LDIFImportConfig(schemaFile));
      Entry entry = reader.readEntry();
      if (entry != null)
      {
        schemaDefined = true;
      }
    }
    catch (Throwable t)
    {
    }
    finally
    {
      StaticUtils.close(reader);
    }

    return schemaDefined;
  }

  /**
   * Returns the list of LDIF lines that are enough to create the entry
   * containing only the schema element associated with this task.
   * @return the list of LDIF lines that are enough to create the entry
   * containing only the schema element associated with this task.
   */
  private ArrayList<String> getSchemaEntryLines()
  {
    ArrayList<String> lines = new ArrayList<>();
    lines.add("dn: cn=schema");
    lines.add("objectClass: top");
    lines.add("objectClass: ldapSubentry");
    lines.add("objectClass: subschema");
    return lines;
  }


  /**
   * Updates the contents of the schema file.
   *
   * @param schemaFile the schema file.
   * @param isSchemaFileDefined whether the schema is defined or not.
   * @param attributes the attributes to add.
   * @param objectClasses the object classes to add.
   * @throws OpenDsException if an error occurs updating the schema file.
   */
  private void updateSchemaFile(String schemaFile,
      boolean isSchemaFileDefined,
      List<AttributeType> attributes,
      List<ObjectClass> objectClasses) throws OpenDsException
  {
    if (isSchemaFileDefined)
    {
      LDIFExportConfig exportConfig =
        new LDIFExportConfig(schemaFile,
                             ExistingFileBehavior.OVERWRITE);
      LDIFReader reader = null;
      LDIFWriter writer = null;
      try
      {
        reader = new LDIFReader(new LDIFImportConfig(schemaFile));
        Entry schemaEntry = reader.readEntry();

        for (AttributeType attribute : attributes)
        {
          Modification mod = new Modification(ModificationType.ADD,
              Attributes.create(getAttributeName(attribute).toLowerCase(),
                  getValueOffline(attribute)));
          schemaEntry.applyModification(mod);
        }
        for (ObjectClass oc : objectClasses)
        {
          Modification mod = new Modification(ModificationType.ADD,
              Attributes.create(getAttributeName(oc).toLowerCase(),
                  getValueOffline(oc)));
          schemaEntry.applyModification(mod);
        }
        writer = new LDIFWriter(exportConfig);
        writer.writeEntry(schemaEntry);
        exportConfig.getWriter().newLine();
      }
      catch (Throwable t)
      {
        throw new OfflineUpdateException(
            ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(t), t);
      }
      finally
      {
        StaticUtils.close(reader, exportConfig, writer);
      }
    }
    else
    {
      LDIFExportConfig exportConfig =
        new LDIFExportConfig(schemaFile,
                             ExistingFileBehavior.FAIL);
      try
      {
        ArrayList<String> lines = getSchemaEntryLines();
        for (AttributeType attribute : attributes)
        {
          lines.add(
              getAttributeName(attribute)+": "+getValueOffline(attribute));
        }
        for (ObjectClass oc : objectClasses)
        {
          lines.add(getAttributeName(oc)+": "+getValueOffline(oc));
        }
        for (String line : lines)
        {
          LDIFWriter.writeLDIFLine(new StringBuilder(line),
              exportConfig.getWriter(), exportConfig.getWrapColumn() > 1,
              exportConfig.getWrapColumn());
        }

        exportConfig.getWriter().newLine();
      }
      catch (Throwable t)
      {
        throw new OfflineUpdateException(
            ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(t), t);
      }
      finally
      {
        StaticUtils.close(exportConfig);
      }
    }
  }
}

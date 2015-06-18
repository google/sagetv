/*
 * Copyright 2015 The SageTV Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sage.miniclient;

public class AddServerDialog extends javax.swing.JDialog implements java.awt.event.ActionListener
{
  public AddServerDialog(java.awt.Frame owner)
  {
    super(owner, "Edit SageTV Server Info", true);
    getContentPane().setLayout(new java.awt.GridBagLayout());
    java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
    nameField = new javax.swing.JTextField(32);
    locatorIDField = new javax.swing.JTextField(32);
    addressField = new javax.swing.JTextField(32);
    directConnectCheck = new javax.swing.JCheckBox("Connect to this server directly by its hostname or IP address");

    gbc.gridx = gbc.gridy = 0;
    gbc.weightx = gbc.weighty = 0;
    gbc.gridwidth = gbc.gridheight = 1;
    gbc.fill = gbc.NONE;
    gbc.insets = new java.awt.Insets(5, 5, 5, 5);
    gbc.ipadx = gbc.ipadx = 4;
    getContentPane().add(new javax.swing.JLabel("Name:"), gbc);
    gbc.gridy++;
    getContentPane().add(new javax.swing.JLabel("Locator ID:"), gbc);
    gbc.gridx++;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.fill = gbc.HORIZONTAL;
    getContentPane().add(nameField, gbc);
    gbc.gridy++;
    getContentPane().add(locatorIDField, gbc);
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.gridwidth = 2;
    getContentPane().add(directConnectCheck, gbc);
    gbc.gridy++;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    getContentPane().add(addressLabel = new javax.swing.JLabel("Address:"), gbc);
    gbc.gridx++;
    gbc.weightx = 1.0;
    getContentPane().add(addressField, gbc);

    directConnectCheck.addActionListener(this);

    javax.swing.JPanel buttonPanel = new javax.swing.JPanel();
    buttonPanel.add(okButton = new javax.swing.JButton("OK"));
    buttonPanel.add(cancelButton = new javax.swing.JButton("Cancel"));
    gbc.gridy++;
    gbc.gridx = 0;
    gbc.gridwidth = 2;
    getContentPane().add(buttonPanel, gbc);
    okButton.addActionListener(this);
    cancelButton.addActionListener(this);
    sage.MySwingUtils.fixOKButton(okButton);
    sage.MySwingUtils.fixCancelButton(cancelButton);
    pack();
    setLocation(owner.getX() + 100, owner.getY() + 100);
  }

  // The current server list is passed in order to validate that there's no duplicate names
  public MiniClientManagerFrame.MgrServerInfo getNewServerInfo(java.util.Vector inCurrServerList)
  {
    setTitle("Add SageTV Server Info");
    currServerList = inCurrServerList;
    currEditInfo = null;
    String nameBase = "My SageTV";
    if (isNameDuplicate(nameBase))
    {
      int num = 2;
      while (isNameDuplicate(nameBase + " " + num))
        num++;
      nameBase = nameBase + " " + num;
    }
    nameField.setText(nameBase);
    nameField.selectAll();
    locatorIDField.setText("");
    directConnectCheck.setSelected(false);
    addressField.setText("");
    addressField.setEnabled(false);
    addressLabel.setEnabled(false);
    okSelected = false;
    setVisible(true);
    if (okSelected)
    {
      MiniClientManagerFrame.MgrServerInfo rv = new MiniClientManagerFrame.MgrServerInfo();
      rv.serverName = nameField.getText().trim();
      rv.serverLookupID = locatorIDField.getText().trim();
      rv.serverDirectAddress = addressField.getText().trim();
      if (directConnectCheck.isSelected())
        rv.serverType = MiniClientManagerFrame.DIRECT_CONNECT_SERVER;
      else
        rv.serverType = MiniClientManagerFrame.LOCATABLE_SERVER;
      if (rv.serverType == MiniClientManagerFrame.LOCATABLE_SERVER)
        rv.doIPLookupNow();
      return rv;
    }
    else
      return null;
  }

  public MiniClientManagerFrame.MgrServerInfo editServerInfo(java.util.Vector inCurrServerList, MiniClientManagerFrame.MgrServerInfo currEdit)
  {
    setTitle("Edit SageTV Server Info");
    currServerList = inCurrServerList;
    currEditInfo = currEdit;
    nameField.setText(currEdit.serverName);
    locatorIDField.setText(currEdit.serverLookupID);
    directConnectCheck.setSelected(currEdit.serverType == MiniClientManagerFrame.DIRECT_CONNECT_SERVER);
    addressField.setText(currEdit.serverDirectAddress);
    addressField.setEnabled(currEdit.serverType == MiniClientManagerFrame.DIRECT_CONNECT_SERVER);
    addressLabel.setEnabled(currEdit.serverType == MiniClientManagerFrame.DIRECT_CONNECT_SERVER);
    okSelected = false;
    setVisible(true);
    if (okSelected)
    {
      MiniClientManagerFrame.MgrServerInfo rv = currEditInfo;
      rv.serverName = nameField.getText().trim();
      rv.serverLookupID = locatorIDField.getText().trim();
      rv.serverDirectAddress = addressField.getText().trim();
      if (directConnectCheck.isSelected())
        rv.serverType = MiniClientManagerFrame.DIRECT_CONNECT_SERVER;
      else
        rv.serverType = MiniClientManagerFrame.LOCATABLE_SERVER;
      if (rv.serverType == MiniClientManagerFrame.LOCATABLE_SERVER)
        rv.doIPLookupNow();
      return rv;
    }
    else
      return null;
  }

  private boolean isNameDuplicate(String test)
  {
    for (int i = 0; i < currServerList.size(); i++)
    {
      MiniClientManagerFrame.MgrServerInfo msi = (MiniClientManagerFrame.MgrServerInfo) currServerList.get(i);
      if (msi == currEditInfo)
        continue;
      if (msi.serverName.equals(test))
      {
        return true;
      }
    }
    return false;
  }

  private boolean validateFields()
  {
    String addrFieldStr = addressField.getText().trim();
    // Check to make sure there is a name and that it's not a duplicate
    if (nameField.getText().trim().length() == 0)
    {
      if (addrFieldStr.length() > 0)
        nameField.setText(addrFieldStr);
      else
      {
        javax.swing.JOptionPane.showMessageDialog(this, "You must enter a name for the new server");
        return false;
      }
    }
    if (nameField.getText().indexOf(';') != -1 || nameField.getText().indexOf('/') != -1)
    {
      javax.swing.JOptionPane.showMessageDialog(this, "The name is not allowed to contain the ; or / characters");
      return false;
    }
    // Check for a duplicate name
    if (isNameDuplicate(nameField.getText().trim()))
    {
      javax.swing.JOptionPane.showMessageDialog(this, "You need to choose a unique name. The name \"" + nameField.getText().trim() + "\" is already used");
      return false;
    }
    // Check for a server address
    if (directConnectCheck.isSelected())
    {
      if (addrFieldStr.length() == 0)
      {
        javax.swing.JOptionPane.showMessageDialog(this, "You need to enter a hostname/IP address in the Address field");
        return false;
      }
    }
    else
    {
      // Make sure we have a valid Locator ID
      String locIDText = locatorIDField.getText().trim();
      java.util.StringTokenizer toker = new java.util.StringTokenizer(locIDText, "-");
      if (toker.countTokens() != 4)
      {
        javax.swing.JOptionPane.showMessageDialog(this, "You have entered an invalid Locator ID. It should be in the form: XXXX-XXXX-XXXX-XXXX");
        return false;
      }
      try
      {
        Integer.parseInt(toker.nextToken(), 16);
        Integer.parseInt(toker.nextToken(), 16);
        Integer.parseInt(toker.nextToken(), 16);
        Integer.parseInt(toker.nextToken(), 16);
      }
      catch (NumberFormatException e)
      {
        javax.swing.JOptionPane.showMessageDialog(this, "You have entered an invalid Locator ID. It contains invalid characters (valid range is 0-9 & A-F)");
        return false;
      }
      if (locIDText.length() != 19 || locIDText.charAt(4) != '-' || locIDText.charAt(9) != '-' || locIDText.charAt(14) != '-')
      {
        javax.swing.JOptionPane.showMessageDialog(this, "You have entered an invalid Locator ID. It should be in the form: XXXX-XXXX-XXXX-XXXX");
        return false;
      }
    }
    return true;
  }

  public void actionPerformed(java.awt.event.ActionEvent e)
  {
    if (e.getSource() == okButton)
    {
      if (validateFields())
      {
        okSelected = true;
        setVisible(false);
      }
    }
    else if (e.getSource() == cancelButton)
    {
      okSelected = false;
      setVisible(false);
    }
    else if (e.getSource() == directConnectCheck)
    {
      addressField.setEnabled(directConnectCheck.isSelected());
      addressLabel.setEnabled(directConnectCheck.isSelected());
    }
  }

  private boolean okSelected;

  private java.util.Vector currServerList;
  private MiniClientManagerFrame.MgrServerInfo currEditInfo;

  private javax.swing.JTextField nameField;
  private javax.swing.JTextField locatorIDField;
  private javax.swing.JTextField addressField;
  private javax.swing.JCheckBox directConnectCheck;

  private javax.swing.JButton okButton;
  private javax.swing.JButton cancelButton;
  private javax.swing.JLabel addressLabel;
}

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
package sage;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class WindowsServiceControl implements ActionListener
{
  public static java.util.Locale userLocale = null;
  private static java.util.ResourceBundle coreRez = null;
  // For message formatting
  private static java.util.Map msgFormatRezMap = new java.util.HashMap();
  static String rez(String rezName, Object[] vars)
  {
    java.text.MessageFormat mf = (java.text.MessageFormat) msgFormatRezMap.get(rezName);
    if (mf == null)
    {
      msgFormatRezMap.put(rezName, mf = new java.text.MessageFormat(rez(rezName), userLocale));
    }
    String rv;
    synchronized (mf)
    {
      rv = mf.format(vars);
    }
    return rv;
  }
  static String rez(String rezName)
  {
    try
    {
      return coreRez.getString(rezName);
    }
    catch (java.util.MissingResourceException e)
    {
      System.out.println("WARNING - MissingResource: \"" + rezName + "\"");
      return rezName;
    }
  }

  public native long openServiceHandle0(String serviceName);
  public native void closeServiceHandle0(long ptr);

  public native boolean isServiceAutostart0(long ptr);
  public native long installService0(String serviceName, String serviceExe);
  public native boolean setServiceAutostart0(long ptr, boolean autostart);
  public native boolean startService0(long ptr);
  public native boolean stopService0(long ptr);
  public native boolean isServiceRunning0(long ptr);
  public native boolean isServiceLoading0(long ptr);
  public native boolean isServiceStopping0(long ptr);
  public native String getServiceUser0(long ptr);
  public native boolean setServiceUser0(long ptr, String username, String password);
  public native boolean isServiceRecovery0(long ptr);
  public native void setServiceRecovery0(long ptr, boolean x);

  public void actionPerformed(ActionEvent e)
  {
    Object src = e.getSource();
    if (src == enableButton)
    {
      if (ptr == 0)
      {
        ptr = installService0("SageTV", new java.io.File(System.getProperty("user.dir"),
            "SageTVService.exe").getAbsolutePath());
        if (ptr == 0)
          javax.swing.JOptionPane.showMessageDialog(f, rez("Service_Install_Error"));
      }
      else
        setServiceAutostart0(ptr, true);
      refreshUIState();
    }
    else if (src == disableButton)
    {
      setServiceAutostart0(ptr, false);
      refreshUIState();
    }
    else if (src == startButton)
    {
      startService0(ptr);
      refreshUIWhileTransient();
    }
    else if (src == stopButton)
    {
      stopService0(ptr);
      refreshUIWhileTransient();
    }
    else if (src == runAsDefaultButton)
    {
      setServiceUser0(ptr, "LocalSystem", "");
      refreshUIState();
    }
    else if (src == runAsUserButton)
    {
      String user = javax.swing.JOptionPane.showInputDialog(f, rez("Enter_Username"), ".\\" + System.getProperty("user.name"));
      if (user != null)
      {
        JPasswordField passwordField = new JPasswordField(20);
        JOptionPane optionPane = new JOptionPane();
        optionPane.setMessage( new Object[] { rez("Enter_Password"), passwordField } );
        optionPane.setMessageType(JOptionPane.QUESTION_MESSAGE);
        optionPane.setOptionType(JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = optionPane.createDialog(f, rez("Enter_Password"));
        dialog.setVisible(true);
        Integer value = (Integer)optionPane.getValue();
        if (value == null || value.intValue() == JOptionPane.CANCEL_OPTION || value.intValue() == JOptionPane.CLOSED_OPTION)
        {
          dialog.dispose();
          return;
        }
        String pass = new String(passwordField.getPassword());
        dialog.dispose();
        if (pass.trim().length() == 0)
        {
          javax.swing.JOptionPane.showMessageDialog(f, rez("Blank_Password_for_Service"));
          return;
        }
        // Now confirm the password
        passwordField = new JPasswordField(20);
        optionPane = new JOptionPane();
        optionPane.setMessage( new Object[] { rez("Confirm_Password"), passwordField } );
        optionPane.setMessageType(JOptionPane.QUESTION_MESSAGE);
        optionPane.setOptionType(JOptionPane.OK_CANCEL_OPTION);
        dialog = optionPane.createDialog(f, rez("Confirm_Password"));
        dialog.setVisible(true);
        value = (Integer)optionPane.getValue();
        if (value == null || value.intValue() == JOptionPane.CANCEL_OPTION || value.intValue() == JOptionPane.CLOSED_OPTION)
        {
          dialog.dispose();
          return;
        }
        String pass2 = new String(passwordField.getPassword());
        if (!pass.equals(pass2))
        {
          javax.swing.JOptionPane.showMessageDialog(f, rez("Password_Mismatch"));
          return;
        }
        if (!setServiceUser0(ptr, user, pass))
        {
          javax.swing.JOptionPane.showMessageDialog(f, rez("Invalid_username_or_password_entered"));
        }
        refreshUIState();
      }
    }
    else if (src == refreshButton)
    {
      refreshUIState();
    }
    else if (src == aboutButton)
    {
      showAbout();
    }
    else if (src == autoRestartServiceCheck)
    {
      if (ptr != 0)
      {
        setServiceRecovery0(ptr, autoRestartServiceCheck.isSelected());
        refreshUIState();
      }
    }
  }

  private void refreshUIWhileTransient()
  {
    refreshUIState();
    Thread t = new Thread()
    {
      public void run()
      {
        while (isLoading() || isStopping())
        {
          try{Thread.sleep(100);}catch(Exception e){}
          refreshUIState();
        }
        refreshUIState();
      }
    };
    t.start();
  }
  JFrame f;
  JLabel enabledLabel;
  JLabel runningLabel;
  JLabel runAsLabel;
  JButton enableButton;
  JButton disableButton;
  JButton startButton;
  JButton stopButton;
  JButton runAsDefaultButton;
  JButton runAsUserButton;
  JButton refreshButton;
  JButton aboutButton;
  JCheckBox autoRestartServiceCheck;

  long ptr;

  private boolean isRunning() { return ptr != 0 && isServiceRunning0(ptr); }
  private boolean isLoading() { return ptr != 0 && isServiceLoading0(ptr); }
  private boolean isStopping() { return ptr != 0 && isServiceStopping0(ptr); }
  private boolean isEnabled() { return ptr != 0 && isServiceAutostart0(ptr); }
  private String getServiceUser() { return ptr == 0 ? "LocalSystem" : getServiceUser0(ptr); }

  void refreshUIState()
  {
    enabledLabel.setText(rez("Use_SageTV_Service") + ": " + (isEnabled() ? rez("Yes") : rez("No")));
    runningLabel.setText(rez("SageTV_Service_State") + ": " + (isRunning() ? rez("Started") :
      (isLoading() ? rez("Starting") : (isStopping() ? rez("Stopping") : rez("Stopped")))));
    runAsLabel.setText(rez("Run_Service_As_User") + ": " + getServiceUser());
    enableButton.setEnabled(!isEnabled());
    disableButton.setEnabled(isEnabled());
    startButton.setEnabled(ptr != 0 && !isRunning() && !isLoading() && !isStopping());
    stopButton.setEnabled(ptr != 0 && isRunning());
    runAsDefaultButton.setEnabled(ptr != 0);
    runAsUserButton.setEnabled(ptr != 0);
    autoRestartServiceCheck.setEnabled(ptr != 0);
    autoRestartServiceCheck.setSelected(ptr != 0 && isServiceRecovery0(ptr));

    f.validate();
  }

  private WindowsServiceControl()
  {
    sage.Native.loadLibrary("SageTVWin32");
    ptr = openServiceHandle0("SageTV");
    f = new JFrame(rez("SageTV_Service_Control"));
    f.addWindowListener(new WindowAdapter()
    {
      public void windowClosing(WindowEvent evt)
      {
        closeServiceHandle0(ptr);
        System.exit(0);
      }
    });

    enabledLabel = new JLabel(rez("Use_SageTV_Service") + ": " + (isEnabled() ? rez("Yes") : rez("No")));
    runningLabel = new JLabel(rez("SageTV_Service_State") + ": " + (isRunning() ? rez("Started") :
      (isLoading() ? rez("Starting") : (isStopping() ? rez("Stopping") : rez("Stopped")))));
    runAsLabel = new JLabel(rez("Run_Service_As_User") + ": " + getServiceUser());

    enableButton = new JButton(rez("Enable"));
    enableButton.addActionListener(this);
    disableButton = new JButton(rez("Disable"));
    disableButton.addActionListener(this);
    startButton = new JButton(rez("Start"));
    startButton.addActionListener(this);
    stopButton = new JButton(rez("Stop"));
    stopButton.addActionListener(this);
    runAsDefaultButton = new JButton("LocalSystem(" + rez("Default") + ")");
    runAsDefaultButton.addActionListener(this);
    runAsUserButton = new JButton(rez("Change_User"));
    runAsUserButton.addActionListener(this);
    autoRestartServiceCheck = new JCheckBox(rez("Enable_Service_Recovery"));
    autoRestartServiceCheck.addActionListener(this);
    refreshButton = new JButton(rez("Refresh"));
    refreshButton.addActionListener(this);
    aboutButton = new JButton(rez("About"));
    aboutButton.addActionListener(this);

    Container pane = f.getContentPane();
    pane.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.gridheight = 1;
    gbc.anchor = gbc.CENTER;
    gbc.insets = new Insets(4, 4, 4, 4);
    pane.add(new JLabel(new ImageIcon("STVs\\SageTV3\\SageLogo256.png")), gbc);
    gbc.gridy++;
    gbc.ipadx = gbc.ipady = 4;
    pane.add(enabledLabel, gbc);

    gbc.gridy++;
    gbc.gridwidth = 1;
    pane.add(enableButton, gbc);
    gbc.gridx++;
    pane.add(disableButton, gbc);

    gbc.gridy++;
    gbc.gridx = 0;
    gbc.gridwidth = 2;
    pane.add(runningLabel, gbc);

    gbc.gridy++;
    gbc.gridwidth = 1;
    pane.add(startButton, gbc);
    gbc.gridx++;
    pane.add(stopButton, gbc);

    gbc.gridy++;
    gbc.gridx = 0;
    gbc.gridwidth = 2;
    pane.add(runAsLabel, gbc);

    gbc.gridy++;
    gbc.gridwidth = 1;
    pane.add(runAsDefaultButton, gbc);
    gbc.gridx++;
    pane.add(runAsUserButton, gbc);

    gbc.gridy++;
    gbc.gridx = 0;
    gbc.gridwidth = 2;
    pane.add(autoRestartServiceCheck, gbc);

    gbc.gridy++;
    gbc.gridx = 0;
    gbc.gridwidth = 1;
    pane.add(refreshButton, gbc);

    gbc.gridx++;
    pane.add(aboutButton, gbc);

    refreshUIState();

    f.pack();
    f.setLocation(200, 200);
    f.setVisible(true);
  }

  public static void main(String[] args)
  {
    Properties props = new Properties();
    InputStream is = null;
    try
    {
      is = new BufferedInputStream(new FileInputStream("Sage.properties"));
      props.load(is);
      is.close();
    }
    catch (Exception e)
    {
      if (is != null)
      {
        try
        {
          is.close();
        }
        catch (Exception e1){}
      }
    }
    String preferredLanguage = props.getProperty("ui/translation_language_code", "");
    String preferredCountry = props.getProperty("ui/translation_country_code", "");
    if (preferredLanguage.length() > 0)
      userLocale = new java.util.Locale(preferredLanguage, preferredCountry);
    else
      userLocale = java.util.Locale.getDefault();
    coreRez = java.util.ResourceBundle.getBundle("SageTVCoreTranslations", userLocale);

    WindowsServiceControl wsc = new WindowsServiceControl();

    if (!UIManager.SAGE.equals(props.getProperty("version", "")))
    {
      // New install or upgrade, so show the about info for help
      wsc.showAbout();
    }
  }

  private JDialog aboutDialog;
  private void showAbout()
  {
    if (aboutDialog != null)
    {
      aboutDialog.setVisible(true);
      return;
    }
    aboutDialog = new JDialog(f, rez("About"), false);
    aboutDialog.getContentPane().setLayout(new BorderLayout());
    JTextArea texty = new JTextArea(rez("SageTV_Service_Info"));
    Font fonty = texty.getFont();
    texty.setFont(fonty.deriveFont(Font.BOLD, fonty.getSize2D() + 2));
    texty.setLineWrap(true);
    texty.setWrapStyleWord(true);
    aboutDialog.getContentPane().add(new JScrollPane(texty), "Center");
    aboutDialog.setSize(450, 300);
    aboutDialog.setLocation(250, 250);
    aboutDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    aboutDialog.setVisible(true);
  }
}

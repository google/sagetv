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

public class WidgetTransfer implements java.awt.datatransfer.Transferable
{
  public static final java.awt.datatransfer.DataFlavor WIDGET_FLAVOR = buildSpecialFlavor("Widget");

  // 601 public WidgetTransfer(DBObject inTransferObject)
  public WidgetTransfer(Widget inTransferObject, UIManager srcUIMgr)
  {
    transferObject = inTransferObject;
    flavorFlavs = new java.awt.datatransfer.DataFlavor[] {
        WIDGET_FLAVOR, buildSpecialFlavor(OracleTree.WIDGET_KEY + srcUIMgr.getLocalUIClientName() + "*" + transferObject.id()) };
  }

  public WidgetTransfer(Object[] inTransferObjects, UIManager srcUIMgr)
  {
    if (inTransferObjects.length == 0)
    {
      throw new IllegalArgumentException("Transfer objects must be greater than zero in length");
    }
    transferObjects = inTransferObjects;
    String s = OracleTree.MULTIPLE_WIDGET_KEYS + srcUIMgr.getLocalUIClientName() + "*";
    for (int i = 0; i < transferObjects.length; i++)
    {
      if (i != 0)
      {
        s += ',';
      }
      if (transferObjects[i] instanceof TreeNodeDuplicate)
        s += ((TreeNodeDuplicate) transferObjects[i]).getSource().id();
      else
        // 601 s += ((DBObject) transferObjects[i]).id;
        s += ((Widget) transferObjects[i]).id();
    }
    flavorFlavs = new java.awt.datatransfer.DataFlavor[] {
        WIDGET_FLAVOR, buildSpecialFlavor(s) };
  }

  public WidgetTransfer(byte inTransferType)
  {
    transferType = inTransferType;
    flavorFlavs = new java.awt.datatransfer.DataFlavor[] {
        WIDGET_FLAVOR, buildSpecialFlavor(OracleTree.WIDGET_TYPE + Widget.TYPES[transferType]) };
  }

  // Ugly hack to get around the D&D proxy needing to know about the correct UIManager to use
  private static Object xferLock = new Object();
  private static UIManager xferUIMgr;

  public static Object getTransferData(UIManager uiMgr, java.awt.datatransfer.Transferable wXfer)
  {
    synchronized (xferLock)
    {
      xferUIMgr = uiMgr;
      try
      {
        Object rv = wXfer.getTransferData(WIDGET_FLAVOR);
        xferUIMgr = null;
        return rv;
      }
      catch (java.awt.datatransfer.UnsupportedFlavorException e)
      {
        throw new RuntimeException("DnD error:" + e);
      }
      catch (java.io.IOException e1)
      {
        throw new RuntimeException("DnD error:" + e1);
      }
    }
  }

  public Object getTransferData(java.awt.datatransfer.DataFlavor inFlavor)
  {
    if (transferObjects != null)
    {
      return transferObjects;
    }
    else if (transferObject != null)
    {
      return transferObject;
    }
    else
    {
      return xferUIMgr.getModuleGroup().addWidget(transferType);
    }
  }

  public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors()
  {
    return flavorFlavs;
  }

  public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor testFlavor)
  {
    for (int i = 0; i < flavorFlavs.length; i++)
    {
      if (flavorFlavs[i].equals(testFlavor))
      {
        return true;
      }
    }
    return false;
  }

  // 601 private DBObject transferObject;
  private Widget transferObject;
  private Object[] transferObjects;
  private byte transferType;
  private java.awt.datatransfer.DataFlavor[] flavorFlavs;

  public static java.awt.datatransfer.DataFlavor buildSpecialFlavor(Class inClazz)
  {
    return buildSpecialFlavor(inClazz.getName());
  }

  public static java.awt.datatransfer.DataFlavor buildSpecialFlavor(String specialName)
  {
    return new java.awt.datatransfer.DataFlavor(
        java.awt.datatransfer.DataFlavor.javaJVMLocalObjectMimeType,
        specialName)
    {
      // And yeah, we need to override 2 equals methods because
      // for some reason, Sun wrote 2.
      public boolean equals(Object o)
      {
        if (o instanceof java.awt.datatransfer.DataFlavor)
        {
          return super.equals(o) &&
              getHumanPresentableName().equals(
                  ((java.awt.datatransfer.DataFlavor) o).getHumanPresentableName());
        }
        else
        {
          return false;
        }
      }

      public boolean equals(java.awt.datatransfer.DataFlavor o)
      {
        if (o instanceof java.awt.datatransfer.DataFlavor)
        {
          return super.equals(o) &&
              getHumanPresentableName().equals(
                  ((java.awt.datatransfer.DataFlavor) o).getHumanPresentableName());
        }
        else
        {
          return false;
        }
      }
    };
  }
}

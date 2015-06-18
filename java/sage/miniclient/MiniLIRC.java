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

import jtux.*;

public class MiniLIRC extends UConstant
{
  boolean alive=true;
  MiniClientConnection myConn1;
  int lircfd;
  String msg="";

  public MiniLIRC(MiniClientConnection myConn)
  {
    System.out.println("Starting MiniLIRC");
    myConn1=myConn;
    Thread the = new Thread("LIRCInputReader")
    {
      public void run()
      {
        try
        {
          lircfd = UNetwork.socket(AF_UNIX, SOCK_STREAM, 0); // No PF_UNIX?
          //System.out.println("got socket "+lircfd);
          if(lircfd>=0)
          {
            UNetwork.s_sockaddr_un lircaddr = new UNetwork.s_sockaddr_un();
            int flags = UFile.fcntl(lircfd, F_GETFL, 0);
            UFile.fcntl(lircfd, F_SETFL, flags|O_NONBLOCK);
            lircaddr.sun_family=AF_UNIX;
            lircaddr.sun_path="/dev/lircd";
            // This shouldn't fail with EINPROGRESS since unix type
            UNetwork.connect(lircfd, lircaddr,0);
            //System.out.println("connected "+lircfd);
            byte eventmsg[] = new byte[1024];
            while(alive)
            {
              int len = 0;
              try
              {
                len = UFile.read(lircfd, eventmsg, 1024); // We should break that by line
              }
              catch(UErrorException e)
              {
                if(e.getCode()==EAGAIN)
                {
                  Thread.sleep(50);
                }
                else
                {
                  throw e;
                }
              }
              if(len>0)
              {
                //System.out.println("Len : " + len);
                msg+=new String(eventmsg, 0, len);
                String[] strs1 = msg.split("\n",-1);
                //System.out.println("str: "+strs1.length);
                for(int i=0;i<strs1.length-1;i++)
                {
                  String[] strs = strs1[i].split(" ");
                  for(int j=0;j<strs.length;j++)
                  {
                    //System.out.println("Strings i:"+i+" j:"+j+" "+ strs[j]);
                    // For now we send code + 8192 until strings are implemented...
                  }
                  if(Integer.valueOf(strs[1],16).intValue()==0)
                  {
                    if(strs[3].toLowerCase().indexOf("hauppauge")!=-1)
                    {    // To match our old values...
                      myConn1.postIREvent(
                          Long.valueOf(strs[0],16).intValue()+8192);
                    }
                    else
                    {
                      myConn1.postIREvent(
                          Long.valueOf(strs[0],16).intValue());
                    }
                  }
                  //System.out.println("After post event");
                }
                msg=strs1[strs1.length-1];
              }
            }
            System.out.println("Stopping MiniLIRC");
          }
          else
          {
            System.out.println("Couldn't create socket");
          }
        }
        catch (Exception e){System.out.println("Exception in MiniLIRC: "+e);}
      }
    };
    the.setDaemon(true);
    the.start();
  }

  void close()
  {
    try
    {
      UFile.close(lircfd);
    }
    catch (Exception e){}
    alive=false;
  }
}

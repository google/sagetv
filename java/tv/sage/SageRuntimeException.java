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
package tv.sage;

/**
 * Base class for SageTV unchecked Exceptions.
 *
 * @author 601
 */
public class SageRuntimeException extends RuntimeException implements SageExceptable
{
  protected final int kind;


  public SageRuntimeException()
  {
    kind = UNKNOWN;
  }

  public SageRuntimeException(String message, int kind)
  {
    super(message);

    this.kind = kind;
  }

  public SageRuntimeException(Throwable cause, int kind)
  {
    super(cause);

    this.kind = kind;
  }

  public SageRuntimeException(String message, Throwable cause, int kind)
  {
    super(message, cause);

    this.kind = kind;
  }

  public int getKind()
  {
    return (kind);
  }

  public boolean isKind(int kind)
  {
    return ((this.kind & kind) != 0);
  }

  public String getMessage()
  {
    return ("kind=" + kind + "; " + super.getMessage());
  }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sage.media.format;

/**
 *
 * @author jvl711
 */
public interface FormatParserPlugin 
{
    public void initialize(java.io.File file);
            
    public String getFormatName();
    
    public long getDuration();
    
    public long getBitrate();
    
    public BitstreamFormat[] getStreamFormats();
    
    public void deconstruct();
}

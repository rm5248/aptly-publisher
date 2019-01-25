package com.rm5248.aptlypublisher;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Represents an Aptly repository - everything that needs to be provided
 * to Aptly for it to create a repo.
 */
public class AptlyRepository {
    
    private String m_name;
    private String m_comment;
    private String m_defaultDistribution;
    private String m_gpgPassword;
    
    @DataBoundConstructor
    public AptlyRepository(){}
    
    @DataBoundSetter
    public void setName( String name ){
        m_name = name;
    }
    
    public String getName(){
        return m_name;
    }
    
    @DataBoundSetter
    public void setComment( String comment ){
        m_comment = comment;
    }
    
    public String getComment(){
        return m_comment;
    }
    
    @DataBoundSetter
    public void setDefaultDistribution( String defaultDistribution ){
        m_defaultDistribution = defaultDistribution;
    }
    
    public String getDefaultDistribution(){
        return m_defaultDistribution;
    }
    
    @DataBoundSetter
    public void setGpgPassword( String gpgPassword ){
        m_gpgPassword = gpgPassword;
    }
    
    public String getGpgPassword(){
        return m_gpgPassword;
    }
}

package com.rm5248.aptlypublisher;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.Run.Artifact;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.ArtifactManager;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class AptlyPublisher extends Recorder implements SimpleBuildStep {
    
    private String m_repositoryName;
    private boolean m_uploadAllDebFiles;
    private boolean m_ignoreDebugPackages;
    private boolean m_removeOldPackages;
    private boolean m_includeSource;
    
    @DataBoundConstructor
    public AptlyPublisher(){}
    
    @DataBoundSetter
    public void setRepositoryName( String repoName ){
        m_repositoryName = repoName;
    }
    
    public String getRepositoryName(){
        return m_repositoryName;
    }
    
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Override
    public void perform(Run run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        AptlyRepository repositoryToUse = null;

        for( AptlyRepository r : getDescriptor().getRepositories() ){
            if( r.getName().equals( getRepositoryName() ) ){
                repositoryToUse = r;
            }
        }
        
        if( repositoryToUse == null ){
            String errorMsg = String.format( "Unable to find repository with name %s: check your global config",
                    getRepositoryName() );
            listener.fatalError( errorMsg );
            return;
        }
        
        AptlyHelper helper = new AptlyHelper( repositoryToUse,
                            run,
                            launcher,
                            listener );
        
        if( m_removeOldPackages ){
            helper.removeOldPackages();
        }
        
        if( !helper.addPackagesToRepo( m_ignoreDebugPackages ) ){
            listener.fatalError( "Can't add packages to repo" );
            throw new AbortException( "Can't add packages to repo" );
        }
        
        helper.dropRepository();
        
        if( !helper.publishRepository() ){
            throw new AbortException( "Can't publish repository" );
        }
        
    }
    
    @Symbol( "aptly-publish" )
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        
        private List<AptlyRepository> m_repositories = new ArrayList<>();
        
        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> type) {
            return true;
        }
        
        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Publish debian packages via Aptly";
        }
        
        public List<AptlyRepository> getRepositories(){
            return m_repositories;
        }
        
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            m_repositories = req.bindJSONToList(AptlyRepository.class, formData.get("repositories"));
            save();
            return super.configure(req, formData);
        }
        
        public ListBoxModel doFillGpgPasswordItems(
                @AncestorInPath Item item,
                @QueryParameter String credentialsId ){

            return CredentialsProvider.listCredentials(
                StandardUsernamePasswordCredentials.class,
                Jenkins.getInstance(),
                ACL.SYSTEM,
                null,
                null
            );
        }
        
        public FormValidation doCheckRepositoryName( @QueryParameter final String repositoryName ){
            for( AptlyRepository repo : m_repositories ){
                if( repo.getName().equals( repositoryName ) ){
                    return FormValidation.ok();
                }
            }
            
            return FormValidation.error( "Can't find repository with that name" );
        }
        
        public ComboBoxModel doFillRepositoryNameItems(){
            ComboBoxModel model = new ComboBoxModel();
            for( AptlyRepository repo : m_repositories ){
                model.add( repo.getName() );
            }
            
            return model;
        }
        
    }

    /**
     * @return the m_uploadAllDebFiles
     */
    public boolean getUploadAllDebFiles() {
        return m_uploadAllDebFiles;
    }

    /**
     * @param m_uploadAllDebFiles the m_uploadAllDebFiles to set
     */
    @DataBoundSetter
    public void setUploadAllDebFiles(boolean m_uploadAllDebFiles) {
        this.m_uploadAllDebFiles = m_uploadAllDebFiles;
    }

    /**
     * @return the m_ignoreDebugPackages
     */
    public boolean getIgnoreDebugPackages() {
        return m_ignoreDebugPackages;
    }

    /**
     * @param m_ignoreDebugPackages the m_ignoreDebugPackages to set
     */
    @DataBoundSetter
    public void setIgnoreDebugPackages(boolean m_ignoreDebugPackages) {
        this.m_ignoreDebugPackages = m_ignoreDebugPackages;
    }

    /**
     * @return the m_removeOldPackages
     */
    public boolean getRemoveOldPackages() {
        return m_removeOldPackages;
    }

    /**
     * @param m_removeOldPackages the m_removeOldPackages to set
     */
    @DataBoundSetter
    public void setRemoveOldPackages(boolean m_removeOldPackages) {
        this.m_removeOldPackages = m_removeOldPackages;
    }

    /**
     * @return the m_includeSource
     */
    public boolean getIncludeSource() {
        return m_includeSource;
    }

    /**
     * @param m_includeSource the m_includeSource to set
     */
    @DataBoundSetter
    public void setIncludeSource(boolean m_includeSource) {
        this.m_includeSource = m_includeSource;
    }
}

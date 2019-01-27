package com.rm5248.aptlypublisher;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.List;
import jenkins.model.ArtifactManager;

/**
 * Does the actual execing of Aplty that is required.
 */
class AptlyHelper {
    
    private final AptlyRepository m_repository;
    private final Run m_run;
    private final List<Run.Artifact> m_artifacts;
    private final Launcher m_launcher;
    private final TaskListener m_listener;
    
    AptlyHelper( AptlyRepository repositoryToUse, 
            Run run,
            Launcher launcher,
            TaskListener listener ){
        m_repository = repositoryToUse;
        m_run = run;
        m_launcher = launcher;
        m_listener = listener;
        m_artifacts = run.getArtifacts();
    }
    
    void removeOldPackages() throws InterruptedException, IOException {
        for( Run.Artifact artifact : m_artifacts ){
            if( !artifact.getFileName().endsWith( ".deb" ) ){
                continue;
            }
            String packageNameToRemove = guessPackageNameFromDebFileName( artifact.getFileName() );
            String debugInfo = String.format( "Removing old package %s", packageNameToRemove );
            m_listener.getLogger().println( debugInfo );

            Launcher.ProcStarter starter =
                    m_launcher.launch()
                    .cmds( "aptly", "repo", "remove", m_repository.getName(), packageNameToRemove )
                    .stderr( m_listener.getLogger() )
                    .stdout( m_listener.getLogger() );

            Proc proc  = starter.start();
            proc.join();
        }
    }
        
    private String guessPackageNameFromDebFileName( String debFileName ){
        return debFileName.substring(0, debFileName.indexOf( '_' ) );
    }
    
    boolean addPackagesToRepo( boolean ignoreDebugPackages ) throws InterruptedException, IOException {
        int status;
        
        for( Run.Artifact artifact : m_artifacts ){
            if( !artifact.getFileName().endsWith( ".deb" ) ){
                continue;
            }
            
            if( ignoreDebugPackages && artifact.getFileName().contains( "-dbg" ) ){
                continue;
            }
            
            String addingPackage = String.format( "Adding package %s to repo %s", 
                artifact.getFileName(), m_repository.getName() );
            m_listener.getLogger().println( addingPackage );

            Launcher.ProcStarter starter =
                    m_launcher.launch()
                    .cmds( "aptly", "repo", "add", m_repository.getName(), getPathForArtifact( m_run.getArtifactManager(), artifact ) )
                    .stderr( m_listener.getLogger() )
                    .stdout( m_listener.getLogger() );

            Proc proc  = starter.start();
            status = proc.join();
            if( status != 0 ){
                String errorMsg = String.format( "Unable to add package(see previous errors)" );
                m_listener.fatalError( errorMsg );
                return false;
            }
        }
        
        return true;
    }
    
    private String getPathForArtifact( ArtifactManager am, Run.Artifact artifact ){
        return Paths.get( am.root().child( artifact.relativePath ).toURI() ).toFile().getAbsolutePath();
    }
    
    void dropRepository() throws InterruptedException, IOException {
        Launcher.ProcStarter starter =
            m_launcher.launch()
            .cmds( "aptly", "publish", "drop", m_repository.getDefaultDistribution(), m_repository.getName() )
            .stderr( m_listener.getLogger() )
            .stdout( m_listener.getLogger() );

            Proc proc  = starter.start();
            proc.join();
    }
    
    boolean publishRepository() throws InterruptedException, IOException {
        StandardUsernamePasswordCredentials creds = CredentialsProvider.findCredentialById(
            m_repository.getGpgPassword(),
            StandardUsernamePasswordCredentials.class,
            m_run );
        int status;
        
        if( creds == null ){
            String errMsg = String.format( "Can't find credentials with ID %s", 
                    m_repository.getGpgPassword() );
            m_listener.fatalError( errMsg );
            return false;
        }
        
        m_listener.getLogger().println( creds.getUsername() );
        m_listener.getLogger().println( creds.getPassword() );
        
        String gpgKey = String.format( "-gpg-key=%s", creds.getUsername() );
        String password = String.format( "-passphrase=%s", creds.getPassword() );
        Launcher.ProcStarter starter2 =
            m_launcher.launch()
            .cmds( "aptly", "publish", "repo", "-batch", gpgKey, password,
                    m_repository.getName(), m_repository.getName() )
            .stderr( m_listener.getLogger() )
            .stdout( m_listener.getLogger() );
        Proc proc2  = starter2.start();
        status = proc2.join();
        
        if( status != 0 ){
            return false;
        }
        
        return true;
    }
}

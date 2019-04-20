package com.rm5248.aptlypublisher;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
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
    
    boolean createRepoIfNeeded() throws InterruptedException, IOException {
        int status;
        boolean found = false;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
        Launcher.ProcStarter starter =
            m_launcher.launch()
            .cmds( "aptly", "repo", "list", "-raw" )
            .stdout( bos );
        Proc proc  = starter.start();
        status = proc.join();
        
        if( status != 0 ){
            /* something has gone terribly wrong */
            return false;
        }
        
        try( Scanner scan = new Scanner( new String( bos.toByteArray(), "UTF-8" ) ) ){
            while( scan.hasNextLine() ){
                String line = scan.nextLine();
                
                if( line.trim().equals( m_repository.getName() ) ){
                    found = true;
                }
            }
        }
        
        if( !found ){
            /* Try to create */
            String comment = String.format( "-comment=%s", m_repository.getComment() );
            String distribution = String.format( "-distribution=%s", m_repository.getDefaultDistribution() );
            starter = m_launcher.launch()
                .cmds( "aptly", "repo", "create", comment, distribution, m_repository.getName() )
                .stdout( m_listener.getLogger() )
                .stderr( m_listener.getLogger() );
            proc  = starter.start();
            status = proc.join();
            
            if( status != 0 ){
                return false;
            }
        }
        
        return true;
    }
    
    private String extractArchFromPackageName( String packageName ){
        int idxUnderscore = packageName.lastIndexOf( '_' );
        int idxDeb = packageName.lastIndexOf( ".deb" );
        
        if( idxUnderscore < 0 ){
            return null;
        }
        
        if( idxDeb < 0 ){
            return null;
        }
        
        return packageName.substring( idxUnderscore + 1, idxDeb );
    }
    
    void removeOldPackages() throws InterruptedException, IOException {
        for( Run.Artifact artifact : m_artifacts ){
            if( !artifact.getFileName().endsWith( ".deb" ) &&
                    !artifact.getFileName().endsWith( ".tar.xz" ) ){
                continue;
            }
            String packageNameToRemove = guessPackageNameFromDebFileName( artifact.getFileName() );
            String arch = extractArchFromPackageName( artifact.getFileName() );
            if( arch != null ){
                packageNameToRemove += "{" + arch + "}";
            }
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
    
    private boolean isFileExtensionValid( List<String> validExtensions, String filename ){
        for( String extension : validExtensions ){
            if( filename.endsWith( extension ) ){
                return true;
            }
        }
        
        return false;
    }
    
    boolean addPackagesToRepo( boolean ignoreDebugPackages, boolean includeSource )
            throws InterruptedException, IOException {
        int status;
        List<String> validFileExtensions = new ArrayList<>();
        
        validFileExtensions.add( ".deb" );
        if( includeSource ){
            validFileExtensions.add( ".dsc" );
        }
        
        for( Run.Artifact artifact : m_artifacts ){
            if( !isFileExtensionValid( validFileExtensions, artifact.getFileName() ) ){
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
        
        String gpgKey = String.format( "-gpg-key=%s", creds.getUsername() );
        String password = String.format( "-passphrase=%s", creds.getPassword() );
        Launcher.ProcStarter starter2 =
            m_launcher.launch()
            .cmds( "aptly", "publish", "repo", "-batch", gpgKey, password,
                    m_repository.getName(), m_repository.getName() )
            .masks( false, false, false, false, false, true,
                    false, false )
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

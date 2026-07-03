@REM Maven Wrapper startup script
@setlocal
set MAVEN_PROJECTBASEDIR=%CD%
if not "%MAVEN_PROJECTBASEDIR%"=="" goto endDetectBaseDir
set "MAVEN_PROJECTBASEDIR=%~dp0.."
:endDetectBaseDir
set "CLASSWORLDS_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain"
"%JAVA_HOME%\bin\java.exe" %MAVEN_OPTS% -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" -classpath "%~dp0.mvn\wrapper\maven-wrapper.jar" %CLASSWORLDS_LAUNCHER% %MAVEN_CONFIG% %*

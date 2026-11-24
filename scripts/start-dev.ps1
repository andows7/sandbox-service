param([switch]$BuildImages)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path (Split-Path $PSScriptRoot -Parent) 'backend')
docker info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) { throw 'Start Docker (Linux containers) first.' }
if ($BuildImages) {
    foreach ($item in @(@('c','sandbox-c-gcc11:1.0'),@('cpp','sandbox-cpp-gcc11:1.0'),@('java','sandbox-java-openjdk17:1.0'),@('python','sandbox-python-python3.10:1.0'))) {
        docker build -t $item[1] (Join-Path 'docker-env' $item[0])
        if ($LASTEXITCODE -ne 0) { throw "Image build failed: $($item[0])" }
    }
}
mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Maven package failed. Stop the running backend before packaging on Windows.' }
Write-Host 'Backend dev: http://localhost:8084 . Start frontend separately on port 5173.'
java -Xms32m -Xmx192m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Xss512k -jar target/sandbox-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev

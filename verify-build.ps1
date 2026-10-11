$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot
if (-not (Test-Path -LiteralPath "./pom.xml")) { throw "Open the directory containing pom.xml" }
if (-not (Test-Path -LiteralPath "./src/main/java/com/example/bakir_khata/BakirKhataApplication.java")) { throw "Main Java source missing" }
& .\mvnw.cmd -U clean compile
if ($LASTEXITCODE -ne 0) { throw "Maven compilation failed. Read the first [ERROR] above." }
$classFile = Join-Path $PSScriptRoot 'target/classes/com/example/bakir_khata/BakirKhataApplication.class'
if (Test-Path -LiteralPath $classFile) {
  Write-Host "PASS: Compiled main class is present." -ForegroundColor Green
  Write-Host "You can now run: .\mvnw.cmd spring-boot:run"
} else { throw "Compile command finished but main class was not found in target/classes." }

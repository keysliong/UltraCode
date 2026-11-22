; UltraCode installer (Inno Setup, modern wizard, Material You artwork)
#ifndef AppVer
  #define AppVer "1.0.0"
#endif

[Setup]
AppId={{5B0C6A1E-2F7D-4C55-9B7E-8F3C1A9D2E41}
AppName=UltraCode
AppVersion={#AppVer}
AppVerName=UltraCode {#AppVer}
AppPublisher=UltraCode
DefaultDirName={localappdata}\Programs\UltraCode
DefaultGroupName=UltraCode
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir=..\build\installer
OutputBaseFilename=UltraCode-Setup-{#AppVer}
SetupIconFile=..\icons\icon.ico
UninstallDisplayIcon={app}\UltraCode.exe
UninstallDisplayName=UltraCode
WizardStyle=modern
WizardSizePercent=110
WizardImageFile=wizard.bmp,wizard-2x.bmp
WizardSmallImageFile=wizard-small.bmp,wizard-small-2x.bmp
WizardImageStretch=yes
Compression=lzma2/max
SolidCompression=yes
CloseApplications=yes

[Languages]
Name: "ru"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "en"; MessagesFile: "compiler:Default.isl"

[Messages]
ru.WelcomeLabel1=Добро пожаловать в UltraCode
ru.WelcomeLabel2=ИИ-агент для программирования: чат с нейросетями, Claude Code по подписке, GitHub и сборка приложений.%n%nНажмите «Далее», чтобы продолжить.
ru.FinishedHeadingLabel=UltraCode установлен

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "..\build\compose\binaries\main\app\UltraCode\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{autoprograms}\UltraCode"; Filename: "{app}\UltraCode.exe"
Name: "{autodesktop}\UltraCode"; Filename: "{app}\UltraCode.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\UltraCode.exe"; Description: "{cm:LaunchProgram,UltraCode}"; Flags: nowait postinstall skipifsilent

[Code]
// Material You colors for the wizard (flat surfaces, purple accent).
procedure InitializeWizard;
begin
  WizardForm.Color := $FFF7FE;
  WizardForm.MainPanel.Color := $FFDDE9;
  WizardForm.WelcomePage.Color := $FFF7FE;
  WizardForm.FinishedPage.Color := $FFF7FE;
  WizardForm.InnerPage.Color := $FFF7FE;
  WizardForm.PageNameLabel.Font.Color := $8B374F;
  WizardForm.WelcomeLabel1.Font.Color := $8B374F;
  WizardForm.FinishedHeadingLabel.Font.Color := $8B374F;
end;

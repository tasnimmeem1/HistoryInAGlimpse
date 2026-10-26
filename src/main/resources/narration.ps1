$ErrorActionPreference = 'Stop'
try {
    Add-Type -AssemblyName System.Speech
    $voice = New-Object System.Speech.Synthesis.SpeechSynthesizer
    $voice.SetOutputToDefaultAudioDevice()
    $voice.Rate = -1
    $voice.Volume = 85
    $story = $env:ADAMS_STORY
    Write-Output 'READY'
    while ($null -ne ($command = [Console]::ReadLine())) {
        switch ($command) {
            'START' { $voice.SpeakAsyncCancelAll(); $voice.Resume(); [void]$voice.SpeakAsync($story) }
            'PAUSE' { $voice.Pause() }
            'PLAY' { $voice.Resume() }
            'STOP' { $voice.SpeakAsyncCancelAll(); $voice.Dispose(); exit 0 }
        }
    }
    $voice.SpeakAsyncCancelAll()
    $voice.Dispose()
} catch {
    Write-Output ('VOICE_ERROR: ' + $_.Exception.Message)
    exit 1
}

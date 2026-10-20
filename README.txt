MINIMAL HOME + FIRE TV KIT (Windows)

A. TV: Settings > My Fire TV > Developer Options > ADB debugging ON, Apps from Unknown Sources ON.
   Note IP: Settings > My Fire TV > About > Network.
B. PowerShell in the folder containing firetv.ps1:
   Set-ExecutionPolicy -Scope Process Bypass
   .\firetv.ps1 setup
   .\firetv.ps1 connect -Ip <TV-IP>        (accept prompt on TV: Always allow)
   .\firetv.ps1 backup
   .\firetv.ps1 list                       (send packages.txt to Claude -> get remove.txt)
   .\firetv.ps1 disable -File remove.txt
C. Build launcher: push this folder to a new GitHub repo, open Actions > build > download minimal-home-apk.
   .\firetv.ps1 install -File .\app-debug.apk
   .\firetv.ps1 wallpapers -File C:\path\to\1080p-jpgs
   .\firetv.ps1 launcher-on                (make Home use it)
D. Undo: .\firetv.ps1 launcher-off   |   .\firetv.ps1 restore

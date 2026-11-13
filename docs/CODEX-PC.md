# Codex through a signed-in Windows PC

This manual recipe reconstructs the evaluated split-session method: the phone
opens an SSH terminal on the PC, and that terminal connects to a Codex engine
running in the PC's signed-in desktop session. OpenAI credentials and the
transport token remain on the PC. The phone keeps its own SSH private key.

The evaluated engine and terminal client both used Codex CLI
**0.167.0-alpha.4.1**. This is an observed version, not a claim that it is the
latest release. Match the engine/client versions and repeat the checks after
upgrades. Keep your own account, model selection, and managed policies.

**Replace every `<PLACEHOLDER>` before using these snippets.** They are freshly
written manual examples requiring local adaptation, not an executed installer.
Installing Outlands Home does not provision SSH, a PC engine, credentials, a
scheduled task, or the Termux helper.

## Why two Windows sessions

In the evaluated setup, SSH authentication and the Codex account worked, but
starting the Windows sandbox runner directly inside the SSH service's Session 0
failed with a pipe-in timeout and Windows process-initialization error
`0xC0000142`. A separate engine in the signed-in desktop session passed actual
Python execution. The SSH-hosted TUI then used that same engine successfully.

```text
Termux -> Tailscale -> dedicated Windows SSH listener -> PC Codex TUI
                                                           |
                                           authenticated PC loopback only
                                                           |
                                      Codex engine in signed-in desktop session
```

The WebSocket connection stays inside the PC. Bind it to `127.0.0.1`; never
expose this listener on a tailnet address or `0.0.0.0`. This method does not
require SSH port forwarding. OpenAI documents the WebSocket app-server transport
as **experimental and unsupported**. Treat the method as a lab integration.

## 1. Prepare the PC account and dedicated SSH access

1. Keep the PC awake, connected to Tailscale, and signed into Windows as
   `<HOST_USER>`. Check the existing local Codex installation with
   `codex --version` and `codex login status`. Complete account setup on the PC
   through its normal login flow if needed. Do not transfer its auth files to
   the phone.
2. Install/configure the Windows built-in OpenSSH Server using Microsoft's
   instructions. Preserve existing SSH services, keys, and configuration. Use a
   dedicated port/configuration for this access if another listener already
   serves a different purpose.
3. Generate a dedicated Ed25519 key inside private Termux HOME. Transfer only
   its `.pub` file to the PC. Keep the private key on the phone. Use a passphrase
   according to your local key policy.
4. Configure a dedicated authorized-key location with the Windows ACLs required
   for the selected account. Windows administrator accounts have different
   default authorized-key handling; check the **effective configuration** rather
   than assuming the default user's file is used.
5. Bind SSH to `<HOST_TAILNET_IPV4>`. Restrict the listener's Windows firewall
   allowance to `<PHONE_TAILNET_IPV4>`, the selected Tailscale interface/address,
   `<SSH_PORT>`, and `sshd.exe`. Keep your tailnet policy equally specific.

Example directives to incorporate into the dedicated SSH configuration:

```text
Port <SSH_PORT>
ListenAddress <HOST_TAILNET_IPV4>
AllowUsers <host_user_lowercase>@<PHONE_TAILNET_IPV4>
PubkeyAuthentication yes
AuthenticationMethods publickey
PasswordAuthentication no
AuthorizedKeysFile <DEDICATED_PC_PUBLIC_KEY_FILE>
AllowAgentForwarding no
AllowTcpForwarding no
DisableForwarding yes
```

This is a configuration fragment, not a complete replacement file. Confirm
supported directives and Windows key-file ACLs locally, then run
`sshd -t -f <PC_SSHD_CONFIG>` and inspect `sshd -T -f <PC_SSHD_CONFIG>` before
starting the listener. The SSH account's normal permissions still apply to its
terminal; Codex's workspace boundary applies separately to model tool execution.

Verify the PC's SSH host public-key fingerprint through a trusted local PC
channel. Place the verified public host key in the phone's dedicated known-hosts
file. For a custom port, its entry uses the form:

```text
[<HOST_TAILNET_IPV4>]:<SSH_PORT> <HOST_PUBLIC_KEY_TYPE> <HOST_PUBLIC_KEY_BASE64>
```

Keep strict host-key checking enabled and investigate a changed key before
replacing the pinned entry.

## 2. Create a private PC transport token

Use a fresh private directory owned by the same PC account that runs the engine
and SSH terminal. The following PowerShell example creates a 32-byte random
token and grants access only to that account, SYSTEM, and local administrators.
It refuses to replace an existing directory or token and never prints the token.

```powershell
$privateDirectory = '<PRIVATE_TOKEN_DIRECTORY>'
$tokenFile = Join-Path $privateDirectory 'codex-transport.token'
if (Test-Path -LiteralPath $privateDirectory) {
    throw 'Choose a new private directory, or inspect the existing setup manually.'
}
New-Item -ItemType Directory -Path $privateDirectory -ErrorAction Stop | Out-Null
$owner = [Security.Principal.WindowsIdentity]::GetCurrent().User
$acl = New-Object Security.AccessControl.DirectorySecurity
$acl.SetAccessRuleProtection($true, $false)
$acl.SetOwner($owner)
$allowed = @($owner,
    [Security.Principal.SecurityIdentifier]::new('S-1-5-18'),
    [Security.Principal.SecurityIdentifier]::new('S-1-5-32-544'))
foreach ($sid in $allowed) {
    $rule = [Security.AccessControl.FileSystemAccessRule]::new(
        $sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $acl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $privateDirectory -AclObject $acl -ErrorAction Stop
$random = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($random) } finally { $rng.Dispose() }
$encoded = [Text.Encoding]::ASCII.GetBytes([Convert]::ToBase64String($random))
$file = [IO.File]::Open($tokenFile, 'CreateNew', 'Write', 'None')
try { $file.Write($encoded, 0, $encoded.Length) } finally { $file.Dispose() }
Remove-Variable random, encoded
```

Verify the resulting directory and inherited file ACL before proceeding. Keep
`<PRIVATE_TOKEN_FILE>` outside repositories, shared storage, phone downloads,
logs, and screenshots. Do not put its contents on a command line. The token
authenticates this local transport; it is separate from OpenAI account auth.

## 3. Run the engine in the signed-in desktop session

From a normal PowerShell terminal opened on the PC desktop as `<HOST_USER>`:

```powershell
codex app-server --listen 'ws://127.0.0.1:<LOOPBACK_PORT>' `
    --ws-auth capability-token --ws-token-file '<PRIVATE_TOKEN_FILE>'
```

Keep this foreground process running for the manual test. Do not launch the
engine from the phone's SSH session or as SYSTEM. Confirm that its process is
in the signed-in desktop session, not Session 0, and that its listener is only
on `127.0.0.1`. `/readyz` confirms listener readiness; it does not establish
successful authentication or sandboxed coding.

The engine must also find real executables for your tools. The evaluated host
needed an explicit PC Python runtime on the engine's process-local `PATH`, with
per-user WindowsApps aliases excluded from that process. Keep such adjustments
local to the engine and verify the actual executable; do not replace system
Python or assume an app-execution alias works for the restricted command user.

## 4. Prepare the PC-side terminal script

Create your own `<PC_CLIENT_SCRIPT>` containing this adapted PowerShell snippet.
This script runs on the PC through SSH; it is not copied to or executed by
Android. Use the same PC account and token file as the engine.

```powershell
$ErrorActionPreference = 'Stop'
$env:CODEX_REMOTE_TOKEN = [IO.File]::ReadAllText('<PRIVATE_TOKEN_FILE>').Trim()
try {
    codex --remote 'ws://127.0.0.1:<LOOPBACK_PORT>' `
        --remote-auth-token-env CODEX_REMOTE_TOKEN `
        --cd '<HOST_WORKSPACE>' --sandbox workspace-write `
        --ask-for-approval on-request --no-alt-screen
    $clientExit = $LASTEXITCODE
} finally {
    Remove-Item Env:CODEX_REMOTE_TOKEN -ErrorAction SilentlyContinue
}
exit $clientExit
```

The raw token is read only on the PC and supplied through a PC process environment
variable. The command line contains only the environment-variable name. Preserve
the existing account/model configuration and managed permissions. Workspace-write
does not itself mean network-disabled: inspect `/status`. The evaluated normal
session showed workspace access, network access, and Ask for approval.

## 5. Connect from Termux

Create a private `~/.ssh/outlands-dev-pc.conf` after substituting your values:

```sshconfig
Host outlands-dev-pc
    HostName <HOST_TAILNET_IPV4>
    User <HOST_USER>
    Port <SSH_PORT>
    IdentityFile ~/.ssh/outlands-dev-phone-ed25519
    IdentitiesOnly yes
    StrictHostKeyChecking yes
    UserKnownHostsFile ~/.ssh/outlands-dev-pc-known-hosts
    PreferredAuthentications publickey
    PasswordAuthentication no
    KbdInteractiveAuthentication no
    ForwardAgent no
    ClearAllForwardings yes
```

Create a private `~/bin/codex-pc` helper and make it executable:

```sh
#!/data/data/com.termux/files/usr/bin/sh
exec ssh -tt -F "$HOME/.ssh/outlands-dev-pc.conf" outlands-dev-pc \
  'powershell.exe -NoLogo -NoProfile -File "<PC_CLIENT_SCRIPT>"'
```

Keep SSH config and known-hosts files private and the private key readable only
by the Termux account. With Tailscale connected on both devices and the engine
running, start `~/bin/codex-pc`. The Outlands Home Codex shortcut relies on this
locally prepared helper; the APK does not create it.

## 6. Check the actual phone workflow

Confirm the remote workspace and `/status` in the phone TUI. Then ask the model
to run a small existing fictional Python program whose only output is
`PHONE-CODEX-READY`, with no file changes or network actions. Verify the actual
command output and exit code, not just the model's description of success.

The evaluated phone TUI ran that program with exit code zero and no edits.
A separate bounded backend test used workspace-write, network disabled, and
approval never. Those test settings do not replace normal on-request approvals
or establish that normal sessions have no network access.

## Optional user logon task

After the manual path works, adapt a per-user task to manage the engine:

| Task property | Intended value |
| --- | --- |
| Principal | The same signed-in PC account |
| Logon type | InteractiveToken; PowerShell's principal cmdlet calls this `Interactive` |
| Run level | Limited; highest privileges unchecked |
| Trigger | That user's logon |
| Action | Windows PowerShell `-NoProfile -WindowStyle Hidden -File "<LOCAL_ENGINE_WRAPPER>"` |
| Execution time limit | Zero/unlimited (`PT0S`) |
| Multiple instances | IgnoreNew |

The locally adapted wrapper must reject Session 0, validate its own engine's
process/session/listener, and wait for that engine's lifetime so the task remains
running. If it uses `Start-Process`, use `-WindowStyle Hidden` and inherit only
the current desktop user's process environment. Preserve unrelated tasks.
This source package supplies no executable Windows host installer or copied
private wrapper. Task-managed engine startup was exercised locally; the logon
trigger and a full reboot, logoff, sleep, or network-switch recovery sequence
were not qualified.

## Limits and references

- The PC must remain awake, signed in, online, and connected to Tailscale.
- Native Android Codex version/help worked in the evaluation, but its Linux
  sandbox failed. The tested kernel also lacked required namespace support.
  This PC method does not establish native Android sandbox compatibility.
- ChatGPT Android app integrity/sign-in and browser account verification are
  separate. The PC method reuses the PC's existing login without changing
  account security or moving its credentials to Android.
- [OpenAI app-server protocol, WebSocket limits, authentication and remote TUI](https://learn.chatgpt.com/docs/app-server)
- [OpenAI Windows sandbox configuration](https://learn.chatgpt.com/docs/windows/windows-sandbox)
- [Microsoft OpenSSH server configuration](https://learn.microsoft.com/en-us/windows-server/administration/openssh/openssh_server_configuration)
- [Microsoft Windows OpenSSH key management](https://learn.microsoft.com/en-us/windows-server/administration/openssh/openssh_keymanagement)

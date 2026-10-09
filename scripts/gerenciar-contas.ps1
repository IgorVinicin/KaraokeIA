# Temporary API verification console; the web panel will use these same routes.
param([string]$ApiUrl = 'https://luqgrtfqlywchlyasaog.supabase.co/functions/v1/accounts-api')
$ErrorActionPreference = 'Stop'
function Read-PrivateText([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $buffer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($buffer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($buffer); $secure.Dispose() }
}
function Invoke-AccountsApi([string]$Route, $Payload, [string]$Token = '') {
    $headers = @{}
    if ($Token) { $headers.Authorization = 'Bearer ' + $Token }
    try {
        return Invoke-RestMethod -Uri ($ApiUrl + $Route) -Method Post -Headers $headers `
            -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes(($Payload | ConvertTo-Json -Depth 6))) -TimeoutSec 30
    } catch {
        # Do not print the request, headers, credentials, or raw upstream errors.
        $code = 'request_failed'
        try { $code = ($_.ErrorDetails.Message | ConvertFrom-Json).error } catch {}
        throw "Falha na API: $code"
    }
}
$taskEmail = Read-Host 'E-mail do administrador criado no Supabase Auth'
$taskPassword = Read-PrivateText 'Senha do administrador'
try { $taskLogin = Invoke-AccountsApi '/admin/login' @{ email = $taskEmail; password = $taskPassword } }
finally { $taskPassword = $null }
$taskToken = $taskLogin.auth.access_token
$taskLogin = $null
try {
    Write-Host 'Administrador autenticado. Tokens e senhas nao serao exibidos.'
    while ($true) {
        Write-Host "`n1 Listar contas | 2 Criar conta | 3 Alterar acesso | 4 Listar planos | 5 Criar plano | 0 Sair"
        $taskChoice = Read-Host 'Opcao'
        if ($taskChoice -eq '0') { break }
        try {
            switch ($taskChoice) {
                '1' { (Invoke-AccountsApi '/admin/accounts' @{} $taskToken).accounts | Format-Table user_id,username,enabled,expires_at,plan_id }
                '2' {
                    $taskUsername = Read-Host 'Usuario (3 a 40 caracteres, sem acentos ou espacos)'
                    $taskDisplay = Read-Host 'Nome do cliente'
                    $taskClientPassword = Read-PrivateText 'Senha inicial (minimo 12 caracteres)'
                    try {
                        $taskResult = Invoke-AccountsApi '/admin/create-account' @{ username = $taskUsername; display_name = $taskDisplay;
                            password = $taskClientPassword; enabled = $false; expires_at = $null; plan_id = $null } $taskToken
                        Write-Host ('Conta criada bloqueada. Ative pela opcao 3. ID: ' + $taskResult.account.user_id)
                    } finally { $taskClientPassword = $null }
                }
                '3' {
                    $taskUserId = Read-Host 'ID da conta'
                    $taskState = Read-Host 'Ativar? (s/n)'
                    if ($taskState -notin @('s','n')) { throw 'Informe s ou n.' }
                    $taskDays = Read-Host 'Validade em dias a partir de agora (vazio = sem vencimento)'
                    $taskExpires = $null
                    if ($taskDays) {
                        $taskNumber = 0
                        if (-not [int]::TryParse($taskDays, [ref]$taskNumber) -or $taskNumber -lt 1 -or $taskNumber -gt 36500) { throw 'Validade invalida.' }
                        $taskExpires = [DateTime]::UtcNow.AddDays($taskNumber).ToString('o')
                    }
                    $taskPlan = Read-Host 'ID do plano (vazio = sem plano)'
                    if (-not $taskPlan) { $taskPlan = $null }
                    $null = Invoke-AccountsApi '/admin/set-access' @{ user_id = $taskUserId; enabled = ($taskState -eq 's');
                        expires_at = $taskExpires; plan_id = $taskPlan } $taskToken
                    Write-Host 'Acesso atualizado; sessoes anteriores foram revogadas.'
                }
                '4' { (Invoke-AccountsApi '/admin/plans' @{} $taskToken).plans | Format-Table id,name,enabled }
                '5' {
                    $taskPlanName = Read-Host 'Nome do plano'
                    $taskPlanResult = Invoke-AccountsApi '/admin/create-plan' @{ name = $taskPlanName } $taskToken
                    Write-Host ('Plano criado: ' + $taskPlanResult.plan.id)
                }
                default { Write-Host 'Opcao invalida.' }
            }
        } catch { Write-Host $_.Exception.Message }
    }
} finally { $taskToken = $null; $taskEmail = $null }

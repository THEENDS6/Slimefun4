param(
    [string] $HostName = "127.0.0.1",
    [int] $Port = 25575,
    [Parameter(Mandatory = $true)] [string] $Password,
    [Parameter(Mandatory = $true)] [string] $CommandText,
    [int] $TimeoutMs = 5000
)

$ErrorActionPreference = "Stop"

function Send-RconPacket {
    param(
        [System.Net.Sockets.NetworkStream] $Stream,
        [int] $Id,
        [int] $Type,
        [string] $Payload
    )

    $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($Payload)
    [int] $length = 4 + 4 + $payloadBytes.Length + 2
    $buffer = New-Object byte[] (4 + $length)
    [Array]::Copy([BitConverter]::GetBytes($length), 0, $buffer, 0, 4)
    [Array]::Copy([BitConverter]::GetBytes($Id), 0, $buffer, 4, 4)
    [Array]::Copy([BitConverter]::GetBytes($Type), 0, $buffer, 8, 4)
    [Array]::Copy($payloadBytes, 0, $buffer, 12, $payloadBytes.Length)
    $Stream.Write($buffer, 0, $buffer.Length)
    $Stream.Flush()
}

function Read-Exact {
    param(
        [System.Net.Sockets.NetworkStream] $Stream,
        [int] $Length
    )

    $buffer = New-Object byte[] $Length
    $offset = 0
    while ($offset -lt $Length) {
        $read = $Stream.Read($buffer, $offset, $Length - $offset)
        if ($read -le 0) {
            throw [System.IO.EndOfStreamException]::new("RCON connection closed while reading packet.")
        }
        $offset += $read
    }
    return $buffer
}

function Read-RconPacket {
    param([System.Net.Sockets.NetworkStream] $Stream)

    $lengthBytes = Read-Exact -Stream $Stream -Length 4
    $length = [BitConverter]::ToInt32($lengthBytes, 0)
    $body = Read-Exact -Stream $Stream -Length $length
    $id = [BitConverter]::ToInt32($body, 0)
    $type = [BitConverter]::ToInt32($body, 4)
    $payloadLength = [Math]::Max(0, $length - 10)
    $payload = [System.Text.Encoding]::UTF8.GetString($body, 8, $payloadLength)
    return [PSCustomObject]@{
        Id = $id
        Type = $type
        Payload = $payload
    }
}

$client = [System.Net.Sockets.TcpClient]::new()
$client.ReceiveTimeout = $TimeoutMs
$client.SendTimeout = $TimeoutMs

try {
    $client.Connect($HostName, $Port)
    $stream = $client.GetStream()

    Send-RconPacket -Stream $stream -Id 1001 -Type 3 -Payload $Password
    $auth = Read-RconPacket -Stream $stream
    if ($auth.Id -eq -1) {
        throw "RCON authentication failed."
    }

    Send-RconPacket -Stream $stream -Id 1002 -Type 2 -Payload $CommandText
    $responses = New-Object System.Collections.Generic.List[string]
    while ($true) {
        try {
            $packet = Read-RconPacket -Stream $stream
            if ($packet.Id -eq 1002 -and -not [string]::IsNullOrWhiteSpace($packet.Payload)) {
                $responses.Add($packet.Payload.Trim())
            }
        } catch [System.IO.EndOfStreamException] {
            break
        } catch [System.Net.Sockets.SocketException] {
            break
        } catch [System.IO.IOException] {
            break
        }
    }

    if ($responses.Count -gt 0) {
        $colorCode = [string][char]0x00A7
        $cleanOutput = (($responses -join [Environment]::NewLine) -replace "$colorCode.", "")
        [Console]::Out.WriteLine($cleanOutput)
    }
} finally {
    $client.Close()
}

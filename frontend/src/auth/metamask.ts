type ProviderRequest = {
  method: string
  params?: unknown[]
}

type MetaMaskProvider = {
  isMetaMask?: boolean
  request: (request: ProviderRequest) => Promise<unknown>
}

type BrowserWindow = Window & {
  ethereum?: MetaMaskProvider
}

type ConnectedWallet = {
  provider: MetaMaskProvider
  walletAddress: string
  chainId: number
}

function getMetaMaskProvider(): MetaMaskProvider {
  const provider = (window as BrowserWindow).ethereum

  if (provider === undefined || provider.isMetaMask !== true) {
    throw new Error('MetaMask is not available in this browser')
  }

  return provider
}

export async function connectMetaMask(): Promise<ConnectedWallet> {
  const provider = getMetaMaskProvider()
  const accounts = await provider.request({ method: 'eth_requestAccounts' })

  if (
    !Array.isArray(accounts) ||
    accounts.length === 0 ||
    typeof accounts[0] !== 'string' ||
    !/^0x[0-9a-f]{40}$/i.test(accounts[0])
  ) {
    throw new Error('MetaMask did not return a wallet account')
  }

  const chainIdHex = await provider.request({ method: 'eth_chainId' })

  if (typeof chainIdHex !== 'string' || !/^0x[0-9a-f]+$/i.test(chainIdHex)) {
    throw new Error('MetaMask returned an invalid chain ID')
  }

  const chainId = Number.parseInt(chainIdHex.slice(2), 16)

  if (!Number.isSafeInteger(chainId) || chainId <= 0) {
    throw new Error('MetaMask returned an unsupported chain ID')
  }

  return {
    provider,
    walletAddress: accounts[0],
    chainId,
  }
}

export async function signMetaMaskMessage(
  provider: MetaMaskProvider,
  walletAddress: string,
  message: string,
): Promise<string> {
  const encodedMessage = `0x${Array.from(
    new TextEncoder().encode(message),
    (byte) => byte.toString(16).padStart(2, '0'),
  ).join('')}`

  const signature = await provider.request({
    method: 'personal_sign',
    params: [encodedMessage, walletAddress],
  })

  if (typeof signature !== 'string') {
    throw new Error('MetaMask returned an invalid signature')
  }

  return signature
}

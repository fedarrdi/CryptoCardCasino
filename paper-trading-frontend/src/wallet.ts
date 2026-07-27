type ProviderRequest = {
  method: string
  params?: unknown[]
}

export type MetaMaskProvider = {
  isMetaMask?: boolean
  request: (request: ProviderRequest) => Promise<unknown>
}

type EthereumWindow = Window & {
  ethereum?: MetaMaskProvider
}

export type ConnectedWallet = {
  provider: MetaMaskProvider
  walletAddress: string
  chainId: number
}

function getMetaMask(): MetaMaskProvider {
  const provider = (window as EthereumWindow).ethereum

  if (provider === undefined || provider.isMetaMask !== true) {
    throw new Error('MetaMask is not available in this browser.')
  }

  return provider
}

async function providerRequest(
  provider: MetaMaskProvider,
  request: ProviderRequest,
): Promise<unknown> {
  try {
    return await provider.request(request)
  } catch (error) {
    if (error instanceof Error) {
      throw error
    }

    throw new Error('MetaMask returned an invalid error response.')
  }
}

export async function connectMetaMask(): Promise<ConnectedWallet> {
  const provider = getMetaMask()
  const accounts = await providerRequest(provider, {
    method: 'eth_requestAccounts',
  })

  if (
    !Array.isArray(accounts) ||
    accounts.length === 0 ||
    typeof accounts[0] !== 'string' ||
    !/^0x[0-9a-f]{40}$/i.test(accounts[0])
  ) {
    throw new Error('MetaMask did not return a valid wallet account.')
  }

  const chainIdHex = await providerRequest(provider, { method: 'eth_chainId' })

  if (typeof chainIdHex !== 'string' || !/^0x[0-9a-f]+$/i.test(chainIdHex)) {
    throw new Error('MetaMask returned an invalid chain ID.')
  }

  const chainId = Number.parseInt(chainIdHex.slice(2), 16)

  if (!Number.isSafeInteger(chainId) || chainId <= 0) {
    throw new Error('MetaMask returned an unsupported chain ID.')
  }

  return {
    provider,
    walletAddress: accounts[0],
    chainId,
  }
}

export async function signMessage(
  provider: MetaMaskProvider,
  walletAddress: string,
  message: string,
): Promise<string> {
  const encodedMessage = `0x${Array.from(
    new TextEncoder().encode(message),
    (byte) => byte.toString(16).padStart(2, '0'),
  ).join('')}`
  const signature = await providerRequest(provider, {
    method: 'personal_sign',
    params: [encodedMessage, walletAddress],
  })

  if (typeof signature !== 'string') {
    throw new Error('MetaMask returned an invalid signature.')
  }

  return signature
}

export type LoginResponse = {
  userId: string
  name: string
}

export async function loginUser(name: string): Promise<LoginResponse> {
  const response = await fetch('/api/users/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ name: name.trim() }),
  })

  if (!response.ok) {
    throw new Error(`Login failed with status ${response.status}`)
  }

  return response.json() as Promise<LoginResponse>
}

import { Capacitor, registerPlugin } from '@capacitor/core'

interface NativeApiKey {
  get(): Promise<{ value: string | null }>
  set(options: { value: string | null }): Promise<void>
}

export const isNativeApiKey = Capacitor.getPlatform() === 'android'
export const nativeApiKey = registerPlugin<NativeApiKey>('RoamApiKey')

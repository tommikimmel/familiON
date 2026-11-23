export interface TrustedDevice {
  id: string;
  deviceName: string;
  createdAt: string;
  expiresAt: string;
}

export interface SecuritySettings {
  twoFactorEnabled: boolean;
  trustedDevices: TrustedDevice[];
}

export interface UpdateSecuritySettingsRequest {
  twoFactorEnabled: boolean;
}

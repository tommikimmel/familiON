export interface SystemLatestRelease {
  version: string;
  title: string;
  releasedAt: string;
}

export interface SystemVersionResponse {
  version: string;
  latestRelease: SystemLatestRelease | null;
}

export interface SystemChangelogEntry {
  version: string;
  title: string;
  changelogMarkdown: string;
  isBreaking: boolean;
  releasedAt: string;
}

export interface BeforeInstallPromptEvent extends Event {
  readonly platforms?: readonly string[];
  readonly userChoice: Promise<{
    outcome: 'accepted' | 'dismissed';
    platform: string;
  }>;
  prompt(): Promise<void>;
}

export type InstallPromptMode = 'none' | 'native' | 'ios';

import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  CreateInvitationRequest,
  Invitation,
  InvitationCreated,
} from '../data-access/family.models';
import { FamilyInvitationsSectionComponent } from './family-invitations-section.component';

@Component({
  standalone: true,
  imports: [FamilyInvitationsSectionComponent],
  template: `
    <fo-family-invitations-section
      [invitations]="invitations()"
      [lastCreated]="lastCreated()"
      [canShare]="canShare()"
      (createInvitation)="createdRequests.push($event)"
      (revokeInvitation)="revokedIds.push($event)"
      (copyLink)="copiedUrls.push($event)"
      (shareLink)="sharedItems.push($event)"
    />
  `,
})
class TestHostComponent {
  readonly invitations = signal<Invitation[]>([
    {
      id: 'inv-1',
      email: 'sofia@familion.test',
      role: 'HIJO',
      familyVisibility: 'FAMILY_SUMMARY',
      expiresAt: '2026-12-01T12:00:00Z',
      createdAt: '2026-11-24T12:00:00Z',
      invitedBy: { id: 'user-1', fullName: 'Ana García' },
    },
  ]);
  readonly lastCreated = signal<InvitationCreated | null>(null);
  readonly canShare = signal(true);

  readonly createdRequests: CreateInvitationRequest[] = [];
  readonly revokedIds: string[] = [];
  readonly copiedUrls: string[] = [];
  readonly sharedItems: InvitationCreated[] = [];
}

describe('FamilyInvitationsSectionComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('shows visibility selector only when HIJO role is selected and emits payload', () => {
    const fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="invite-visibility-field"]')).toBeNull();

    const roleSelect = el.querySelector('#invite-role') as HTMLSelectElement;
    roleSelect.value = 'HIJO';
    roleSelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="invite-visibility-field"]')).toBeTruthy();

    const emailInput = el.querySelector('#invite-email') as HTMLInputElement;
    emailInput.value = 'Mateo@Familion.test';
    emailInput.dispatchEvent(new Event('input'));

    const visibilitySelect = el.querySelector('#invite-visibility') as HTMLSelectElement;
    visibilitySelect.value = 'FAMILY_FULL';
    visibilitySelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const form = el.querySelector('[data-testid="invitation-form"]') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.componentInstance.createdRequests).toEqual([
      {
        email: 'mateo@familion.test',
        role: 'HIJO',
        familyVisibility: 'FAMILY_FULL',
      },
    ]);
  });

  it('displays shareable inviteUrl controls and emits copy, share, and revoke events', () => {
    const fixture = TestBed.createComponent(TestHostComponent);
    const created: InvitationCreated = {
      id: 'inv-2',
      email: 'carlos@familion.test',
      role: 'ADULTO',
      familyVisibility: 'FAMILY_FULL',
      expiresAt: '2026-12-01T12:00:00Z',
      createdAt: '2026-11-24T12:00:00Z',
      invitedBy: { id: 'user-1', fullName: 'Ana García' },
      inviteUrl: 'https://familion.app/auth/invite?token=xyz',
    };
    fixture.componentInstance.lastCreated.set(created);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const urlInput = el.querySelector('[data-testid="created-invitation-url"]') as HTMLInputElement;
    expect(urlInput.value).toBe('https://familion.app/auth/invite?token=xyz');

    const copyBtn = el.querySelector(
      '[data-testid="copy-invite-link-btn"] button',
    ) as HTMLButtonElement;
    copyBtn.click();
    expect(fixture.componentInstance.copiedUrls).toEqual([
      'https://familion.app/auth/invite?token=xyz',
    ]);

    const shareBtn = el.querySelector(
      '[data-testid="share-invite-link-btn"] button',
    ) as HTMLButtonElement;
    shareBtn.click();
    expect(fixture.componentInstance.sharedItems).toEqual([created]);

    const revokeBtn = el.querySelector(
      '[data-testid="revoke-invitation-btn"] button',
    ) as HTMLButtonElement;
    revokeBtn.click();
    expect(fixture.componentInstance.revokedIds).toEqual(['inv-1']);

    fixture.componentInstance.canShare.set(false);
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="share-invite-link-btn"]')).toBeNull();
  });
});

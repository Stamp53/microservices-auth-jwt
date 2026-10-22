import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdmissionService } from '../../services/admission.service';
import { StudentRegistrationDto } from '../../models';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="page-container">
      <div class="card form-card">
        <div class="header">
          <h2>Student Online Admission</h2>
          <p>Submit your application for academic admission</p>
        </div>

        @if (successData) {
          <div class="success-box">
            <div class="success-icon">🎉</div>
            <h3>Application Submitted Successfully!</h3>
            <p>Your unique Application Number is:</p>
            <div class="app-number-display">{{ successData.applicationNumber }}</div>
            <p class="success-note">
              Please save this application number. You can track your admission status at any time, or log in with your credentials.
            </p>
            <div class="success-actions">
              <a [routerLink]="['/track']" [queryParams]="{ appNum: successData.applicationNumber }" class="btn btn-primary">
                Track Application Now
              </a>
              <a routerLink="/login" class="btn btn-secondary">
                Go to Sign In
              </a>
            </div>
          </div>
        } @else {
          @if (errorMessage) {
            <div class="alert alert-error">
              {{ errorMessage }}
            </div>
          }

          <form (ngSubmit)="onSubmit()" class="application-form">
            <h4 class="section-title">1. Account Information</h4>
            <div class="grid-2">
              <div class="form-group">
                <label for="username">Username *</label>
                <input
                  id="username"
                  type="text"
                  name="username"
                  [(ngModel)]="form.username"
                  required
                  placeholder="e.g. jdoe24"
                  class="form-control"
                />
              </div>

              <div class="form-group">
                <label for="password">Password *</label>
                <input
                  id="password"
                  type="password"
                  name="password"
                  [(ngModel)]="form.password"
                  required
                  placeholder="Min 6 characters"
                  class="form-control"
                />
              </div>
            </div>

            <h4 class="section-title">2. Personal Details</h4>
            <div class="grid-2">
              <div class="form-group">
                <label for="studentName">Student Full Name *</label>
                <input
                  id="studentName"
                  type="text"
                  name="studentName"
                  [(ngModel)]="form.studentName"
                  required
                  placeholder="Full Legal Name"
                  class="form-control"
                />
              </div>

              <div class="form-group">
                <label for="email">Email Address *</label>
                <input
                  id="email"
                  type="email"
                  name="email"
                  [(ngModel)]="form.email"
                  required
                  placeholder="student@example.com"
                  class="form-control"
                />
              </div>

              <div class="form-group">
                <label for="phone">Phone Number *</label>
                <input
                  id="phone"
                  type="tel"
                  name="phone"
                  [(ngModel)]="form.phone"
                  required
                  placeholder="10-digit mobile number"
                  class="form-control"
                />
              </div>

              <div class="form-group">
                <label for="dob">Date of Birth *</label>
                <input
                  id="dob"
                  type="date"
                  name="dob"
                  [(ngModel)]="form.dateOfBirth"
                  required
                  class="form-control"
                />
              </div>

              <div class="form-group">
                <label for="gender">Gender *</label>
                <select id="gender" name="gender" [(ngModel)]="form.gender" required class="form-control">
                  <option value="Male">Male</option>
                  <option value="Female">Female</option>
                  <option value="Other">Other</option>
                </select>
              </div>

              <div class="form-group">
                <label for="address">Residential Address *</label>
                <input
                  id="address"
                  type="text"
                  name="address"
                  [(ngModel)]="form.address"
                  required
                  placeholder="Street, City, Postal Code"
                  class="form-control"
                />
              </div>
            </div>

            <h4 class="section-title">3. Academic Admission Details</h4>
            <div class="grid-2">
              <div class="form-group">
                <label for="appliedClass">Applying For Class *</label>
                <select id="appliedClass" name="appliedClass" [(ngModel)]="form.appliedClass" required class="form-control">
                  <option value="Class 10">Class 10</option>
                  <option value="Class 11 Science">Class 11 Science</option>
                  <option value="Class 11 Commerce">Class 11 Commerce</option>
                  <option value="Class 11 Arts">Class 11 Arts</option>
                  <option value="Class 12 Science">Class 12 Science</option>
                </select>
              </div>

              <div class="form-group">
                <label for="marks">Previous Marks / Percentage (%) *</label>
                <input
                  id="marks"
                  type="number"
                  step="0.1"
                  min="0"
                  max="100"
                  name="marks"
                  [(ngModel)]="form.marksPercentage"
                  required
                  placeholder="e.g. 88.5"
                  class="form-control"
                />
              </div>

              <div class="form-group full-width">
                <label for="previousSchool">Previous School Name *</label>
                <input
                  id="previousSchool"
                  type="text"
                  name="previousSchool"
                  [(ngModel)]="form.previousSchool"
                  required
                  placeholder="Name of last attended school / institution"
                  class="form-control"
                />
              </div>
            </div>

            <div class="form-actions">
              <button type="submit" class="btn btn-primary submit-btn" [disabled]="loading">
                {{ loading ? 'Submitting Application...' : 'Submit Application' }}
              </button>
            </div>
          </form>
        }
      </div>
    </div>
  `,
  styles: [`
    .page-container {
      max-width: 800px;
      margin: 2.5rem auto;
      padding: 0 1rem;
    }
    .form-card {
      background: white;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 2.5rem;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
    }
    .header {
      margin-bottom: 2rem;
      border-bottom: 1px solid #f1f5f9;
      padding-bottom: 1.25rem;
    }
    .header h2 {
      margin: 0 0 0.4rem 0;
      color: #0f172a;
      font-size: 1.75rem;
    }
    .header p {
      margin: 0;
      color: #64748b;
      font-size: 0.95rem;
    }
    .section-title {
      color: #1e293b;
      font-size: 1.05rem;
      margin: 1.5rem 0 1rem 0;
      padding-bottom: 0.4rem;
      border-bottom: 1px solid #e2e8f0;
    }
    .grid-2 {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1.2rem;
    }
    @media (max-width: 640px) {
      .grid-2 {
        grid-template-columns: 1fr;
      }
    }
    .full-width {
      grid-column: 1 / -1;
    }
    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }
    .form-group label {
      font-size: 0.85rem;
      font-weight: 600;
      color: #334155;
    }
    .form-control {
      padding: 0.65rem 0.85rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.95rem;
      transition: border-color 0.15s;
    }
    .form-control:focus {
      outline: none;
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
    }
    .form-actions {
      margin-top: 2rem;
      display: flex;
      justify-content: flex-end;
    }
    .submit-btn {
      padding: 0.75rem 2rem;
      font-size: 1rem;
      font-weight: 600;
    }
    .btn {
      border: none;
      border-radius: 6px;
      cursor: pointer;
      text-decoration: none;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      transition: background 0.15s;
    }
    .btn-primary {
      background: #2563eb;
      color: white;
    }
    .btn-primary:hover:not(:disabled) {
      background: #1d4ed8;
    }
    .btn-secondary {
      background: #f1f5f9;
      color: #334155;
      padding: 0.75rem 1.5rem;
      font-weight: 600;
    }
    .btn-secondary:hover {
      background: #e2e8f0;
    }
    .alert-error {
      background: #fef2f2;
      border: 1px solid #fecaca;
      color: #b91c1c;
      padding: 0.85rem 1.25rem;
      border-radius: 6px;
      margin-bottom: 1.5rem;
      font-size: 0.9rem;
    }
    .success-box {
      text-align: center;
      padding: 2rem 1rem;
    }
    .success-icon {
      font-size: 3rem;
      margin-bottom: 0.5rem;
    }
    .success-box h3 {
      color: #15803d;
      margin: 0 0 0.5rem 0;
      font-size: 1.5rem;
    }
    .app-number-display {
      display: inline-block;
      font-family: monospace;
      font-size: 1.6rem;
      font-weight: 700;
      color: #1e3a8a;
      background: #eff6ff;
      border: 2px dashed #3b82f6;
      padding: 0.6rem 1.5rem;
      border-radius: 8px;
      margin: 1rem 0;
      letter-spacing: 0.05em;
    }
    .success-note {
      color: #64748b;
      max-width: 480px;
      margin: 0 auto 1.5rem auto;
      font-size: 0.95rem;
    }
    .success-actions {
      display: flex;
      justify-content: center;
      gap: 1rem;
    }
  `]
})
export class RegisterComponent {
  form: StudentRegistrationDto = {
    username: '',
    password: '',
    studentName: '',
    fullName: '',
    email: '',
    phone: '',
    dateOfBirth: '',
    gender: 'Male',
    address: '',
    appliedClass: 'Class 11 Science',
    previousSchool: '',
    marksPercentage: 80
  };

  loading = false;
  errorMessage = '';
  successData: { success: boolean; applicationNumber: string } | null = null;

  constructor(private admissionService: AdmissionService) {}

  onSubmit(): void {
    this.loading = true;
    this.errorMessage = '';

    const payload = {
      ...this.form,
      fullName: this.form.studentName || this.form.fullName,
      studentName: this.form.studentName || this.form.fullName
    };

    this.admissionService.registerStudent(payload as any).subscribe({
      next: (res) => {
        this.loading = false;
        this.successData = res;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err?.error?.error || err?.error?.message || 'Failed to submit application. Please check your inputs.';
      }
    });
  }
}
